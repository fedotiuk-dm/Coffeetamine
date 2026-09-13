package ua.coffeetamine.integration.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import ua.coffeetamine.integration.BaseIntegrationTest;
import ua.coffeetamine.integration.TestAuthSupport;
import ua.coffeetamine.interests.domain.repository.UserInterestSelectionRepository;
import ua.coffeetamine.moodboard.domain.repository.MoodBoardRepository;
import ua.coffeetamine.user.domain.repository.UserProfileRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Locks down the load-bearing invariant: {@code POST /api/users/me/onboarding} writes profile +
 * interests + mood-board inside one transaction. The happy path proves all three are persisted; the
 * failure paths prove the controller surfaces a structured error code with the right HTTP status.
 *
 * <p>The class-level {@code @Transactional} (from {@link BaseIntegrationTest}) makes service and
 * test share a single transaction — by design for isolation between tests, but it means
 * "rolled-back rows are invisible to the test" cannot be asserted here (the rollback only happens
 * at test-method end, after assertions run). DB-rollback atomicity is enforced by Spring's
 * {@code @Transactional} on the service and is exercised by unit / DataJpa tests, not at this
 * layer.
 */
class OnboardingAtomicityIT extends BaseIntegrationTest {

  @Autowired JsonMapper jsonMapper;
  @Autowired TestAuthSupport authSupport;
  @Autowired UserProfileRepository userProfileRepository;
  @Autowired UserInterestSelectionRepository selectionRepository;
  @Autowired MoodBoardRepository moodBoardRepository;

  @Test
  @DisplayName("happy path: profile + interests + mood-board all persisted")
  void completesOnboardingAtomically() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID interestA = testData.createInterestTag("coffee", "Coffee", "drinks");
    UUID interestB = testData.createInterestTag("books", "Books", "culture");

    String payload =
        jsonMapper.writeValueAsString(
            buildOnboardingPayload(List.of(interestA, interestB), validUnsplashImages()));

    mockMvc
        .perform(
            post("/api/users/me/onboarding")
                .with(authSupport.userJwt(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Dima"))
        .andExpect(jsonPath("$.onboardingCompleted").value(true));

    assertThat(userProfileRepository.findById(userId))
        .as("profile row")
        .hasValueSatisfying(p -> assertThat(p.isOnboardingCompleted()).isTrue());
    assertThat(selectionRepository.findByUserIdIn(List.of(userId)))
        .as("interest selections")
        .hasSize(2);
    assertThat(moodBoardRepository.findById(userId))
        .as("mood board")
        .hasValueSatisfying(b -> assertThat(b.getImages().images()).hasSize(6));
  }

  @Test
  @DisplayName("rollback: bad interest tag fails the whole onboarding")
  void rollsBackOnUnknownInterest() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID realInterest = testData.createInterestTag("yoga", "Yoga", "wellness");
    UUID unknownInterest = UUID.randomUUID();

    String payload =
        jsonMapper.writeValueAsString(
            buildOnboardingPayload(List.of(realInterest, unknownInterest), validUnsplashImages()));

    mockMvc
        .perform(
            post("/api/users/me/onboarding")
                .with(authSupport.userJwt(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("INT_TAG_001"));
  }

  @Test
  @DisplayName("rollback: non-Unsplash image URL fails the whole onboarding")
  void rollsBackOnNonUnsplashImage() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID realInterest = testData.createInterestTag("ramen", "Ramen", "food");

    String payload =
        jsonMapper.writeValueAsString(
            buildOnboardingPayload(List.of(realInterest), withOneBadImage(validUnsplashImages())));

    mockMvc
        .perform(
            post("/api/users/me/onboarding")
                .with(authSupport.userJwt(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("MOOD_002"));
  }

  private static java.util.Map<String, Object> buildOnboardingPayload(
      List<UUID> interestIds, List<Map<String, String>> images) {
    return java.util.Map.of(
        "name", "Dima",
        "interestIds", interestIds,
        "moodBoardImages", images);
  }

  private static List<java.util.Map<String, String>> validUnsplashImages() {
    return java.util.stream.IntStream.range(0, 6)
        .mapToObj(
            i ->
                java.util.Map.of(
                    "unsplashPhotoId", "photo" + i,
                    "imageUrl", "https://images.unsplash.com/photo-" + i,
                    "photographerName", "Test " + i,
                    "photographerProfileUrl", "https://unsplash.com/@test" + i,
                    "attributionUrl", "https://unsplash.com/photos/photo" + i))
        .toList();
  }

  private static List<java.util.Map<String, String>> withOneBadImage(
      List<java.util.Map<String, String>> base) {
    var copy = new java.util.ArrayList<>(base);
    copy.set(
        3,
        java.util.Map.of(
            "unsplashPhotoId", "stolen",
            "imageUrl", "https://evil.example.com/photo.jpg",
            "photographerName", "Mallory",
            "photographerProfileUrl", "https://evil.example.com/@mallory",
            "attributionUrl", "https://evil.example.com/photos/stolen"));
    return copy;
  }
}
