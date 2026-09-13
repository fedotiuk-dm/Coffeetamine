package ua.coffeetamine.integration.moodboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import ua.coffeetamine.integration.BaseIntegrationTest;
import ua.coffeetamine.integration.TestAuthSupport;
import ua.coffeetamine.moodboard.domain.model.MoodBoardImageRef;
import ua.coffeetamine.moodboard.domain.repository.MoodBoardRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Locks down the §4.3 mood-board invariants: replacement is atomic, exactly 6 images required (5
 * fails OpenAPI bean-validation → 400 VAL_002), and non-Unsplash URLs are rejected with MOOD_002.
 * Onboarding precondition is enforced (USER_002).
 */
class MoodBoardReplaceIT extends BaseIntegrationTest {

  @Autowired JsonMapper jsonMapper;
  @Autowired TestAuthSupport authSupport;
  @Autowired MoodBoardRepository moodBoardRepository;

  @Test
  @DisplayName("PUT /api/users/me/mood-board — replaces all 6 images atomically")
  void replaceMoodBoardHappyPath() throws Exception {
    UUID userId = testData.createOnboardedUser("Dima");
    testData.seedSixImageMoodBoard(userId);

    String payload = jsonMapper.writeValueAsString(Map.of("images", unsplashImages(6, "fresh-")));

    mockMvc
        .perform(
            put("/api/users/me/mood-board")
                .with(authSupport.userJwt(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.images").isArray())
        .andExpect(jsonPath("$.images.length()").value(6))
        .andExpect(jsonPath("$.images[0].unsplashPhotoId").value("fresh-0"));

    flushAndClear();
    assertThat(moodBoardRepository.findById(userId).orElseThrow().getImages().images())
        .as("only the 6 new images persist — no orphan rows from the previous board")
        .hasSize(6)
        .extracting(MoodBoardImageRef::unsplashPhotoId)
        .allMatch(id -> id.startsWith("fresh-"));
  }

  @Test
  @DisplayName("PUT with 5 images → 400 (OpenAPI bean validation on minItems=6)")
  void replaceWithWrongImageCountReturnsBadRequest() throws Exception {
    UUID userId = testData.createOnboardedUser("Dima");
    testData.seedSixImageMoodBoard(userId);

    String payload = jsonMapper.writeValueAsString(Map.of("images", unsplashImages(5, "short-")));

    mockMvc
        .perform(
            put("/api/users/me/mood-board")
                .with(authSupport.userJwt(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("PUT with non-Unsplash URL → 400 MOOD_002, original board unchanged")
  void replaceWithNonUnsplashRejected() throws Exception {
    UUID userId = testData.createOnboardedUser("Dima");
    testData.seedSixImageMoodBoard(userId);

    List<Map<String, String>> images = new java.util.ArrayList<>(unsplashImages(6, "valid-"));
    images.set(
        2,
        Map.of(
            "unsplashPhotoId", "stolen",
            "imageUrl", "https://evil.example.com/photo.jpg",
            "photographerName", "Mallory",
            "photographerProfileUrl", "https://evil.example.com/@mallory",
            "attributionUrl", "https://evil.example.com/photos/stolen"));

    String payload = jsonMapper.writeValueAsString(Map.of("images", images));

    mockMvc
        .perform(
            put("/api/users/me/mood-board")
                .with(authSupport.userJwt(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("MOOD_002"));

    flushAndClear();
    assertThat(moodBoardRepository.findById(userId).orElseThrow().getImages().images())
        .as("transaction rolled back — original stub board preserved")
        .extracting(MoodBoardImageRef::unsplashPhotoId)
        .allMatch(id -> id.startsWith("stub-photo-"));
  }

  @Test
  @DisplayName("PUT with Unsplash look-alike host → 400 MOOD_002, original board unchanged")
  void replaceWithSpoofedUnsplashSubdomainRejected() throws Exception {
    UUID userId = testData.createOnboardedUser("Dima");
    testData.seedSixImageMoodBoard(userId);

    List<Map<String, String>> images = new java.util.ArrayList<>(unsplashImages(6, "valid-"));
    images.set(
        2,
        Map.of(
            "unsplashPhotoId", "spoofed",
            "imageUrl", "https://evilunsplash.com/photo.jpg",
            "photographerName", "Mallory",
            "photographerProfileUrl", "https://evilunsplash.com/@mallory",
            "attributionUrl", "https://evilunsplash.com/photos/spoofed"));

    String payload = jsonMapper.writeValueAsString(Map.of("images", images));

    mockMvc
        .perform(
            put("/api/users/me/mood-board")
                .with(authSupport.userJwt(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("MOOD_002"));

    flushAndClear();
    assertThat(moodBoardRepository.findById(userId).orElseThrow().getImages().images())
        .as("look-alike host rejected — original stub board preserved")
        .extracting(MoodBoardImageRef::unsplashPhotoId)
        .allMatch(id -> id.startsWith("stub-photo-"));
  }

  @Test
  @DisplayName("PUT for non-onboarded user → 404 USER_002")
  void replaceBlockedForNonOnboarded() throws Exception {
    UUID lurker = testData.createIncompleteUser("Lurker");
    String payload = jsonMapper.writeValueAsString(Map.of("images", unsplashImages(6, "lurker-")));

    mockMvc
        .perform(
            put("/api/users/me/mood-board")
                .with(authSupport.userJwt(lurker))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("USER_002"));
  }

  @Test
  @DisplayName("GET /api/users/{userId}/mood-board — public view returns 6 images")
  void publicMoodBoardReadable() throws Exception {
    UUID owner = testData.createOnboardedUser("Owner");
    UUID viewer = testData.createOnboardedUser("Viewer");
    testData.seedSixImageMoodBoard(owner);

    mockMvc
        .perform(get("/api/users/{userId}/mood-board", owner).with(authSupport.userJwt(viewer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.images.length()").value(6));
  }

  private static List<Map<String, String>> unsplashImages(int count, String idPrefix) {
    return IntStream.range(0, count)
        .mapToObj(
            i ->
                Map.of(
                    "unsplashPhotoId", idPrefix + i,
                    "imageUrl", "https://images.unsplash.com/photo-" + idPrefix + i,
                    "photographerName", "Photographer " + i,
                    "photographerProfileUrl", "https://unsplash.com/@photographer" + i,
                    "attributionUrl", "https://unsplash.com/photos/" + idPrefix + i))
        .toList();
  }
}
