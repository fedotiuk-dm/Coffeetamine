package ua.coffeetamine.integration.interests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import ua.coffeetamine.integration.BaseIntegrationTest;
import ua.coffeetamine.integration.TestAuthSupport;
import ua.coffeetamine.interests.domain.repository.UserInterestSelectionRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Locks down the public interest-tag contract: catalog reads work for any authenticated user,
 * {@code PUT /api/users/me/interests} performs a full atomic replacement of the selection, and
 * refuses to touch a user that has not completed onboarding (USER_002).
 */
class InterestsCatalogIT extends BaseIntegrationTest {

  @Autowired JsonMapper jsonMapper;
  @Autowired TestAuthSupport authSupport;
  @Autowired UserInterestSelectionRepository selectionRepository;

  @Test
  @DisplayName("GET /api/interests — returns active tags, paginated")
  void listInterestsReturnsActiveTags() throws Exception {
    UUID caller = testData.createOnboardedUser("Caller");
    testData.createInterestTag("coffee", "Coffee", "drinks");
    testData.createInterestTag("books", "Books", "culture");

    mockMvc
        .perform(get("/api/interests").with(authSupport.userJwt(caller)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[?(@.code == 'coffee')]").exists())
        .andExpect(jsonPath("$.content[?(@.code == 'books')]").exists());
  }

  @Test
  @DisplayName("PUT /api/users/me/interests — atomic replace, order preserved")
  void replaceMyInterestsAtomically() throws Exception {
    UUID userId = testData.createOnboardedUser("Dima");
    UUID tagA = testData.createInterestTag("coffee", "Coffee", "drinks");
    UUID tagB = testData.createInterestTag("books", "Books", "culture");
    UUID tagC = testData.createInterestTag("hiking", "Hiking", "outdoor");

    // Seed initial selection [A,B]; PUT replaces with [C,A] — must drop B entirely.
    testData.selectInterests(userId, List.of(tagA, tagB));

    String payload =
        jsonMapper.writeValueAsString(
            java.util.Map.of("interestIds", List.of(tagC.toString(), tagA.toString())));

    mockMvc
        .perform(
            put("/api/users/me/interests")
                .with(authSupport.userJwt(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.interests[0].code").value("hiking"))
        .andExpect(jsonPath("$.interests[1].code").value("coffee"));

    flushAndClear();
    assertThat(selectionRepository.findByUserIdIn(List.of(userId)))
        .extracting(s -> s.getInterest().getCode())
        .containsExactlyInAnyOrder("hiking", "coffee");
  }

  @Test
  @DisplayName("PUT /api/users/me/interests with unknown tag → 404 INT_TAG_001")
  void unknownTagReturnsInterestTagNotFound() throws Exception {
    UUID userId = testData.createOnboardedUser("Dima");
    UUID realTag = testData.createInterestTag("coffee", "Coffee", "drinks");
    UUID ghostTag = UUID.randomUUID();

    String payload =
        jsonMapper.writeValueAsString(
            java.util.Map.of("interestIds", List.of(realTag.toString(), ghostTag.toString())));

    mockMvc
        .perform(
            put("/api/users/me/interests")
                .with(authSupport.userJwt(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("INT_TAG_001"));
  }

  @Test
  @DisplayName("PUT /api/users/me/interests for non-onboarded user → 404 USER_002")
  void replaceBlockedForNonOnboarded() throws Exception {
    UUID lurker = testData.createIncompleteUser("Lurker");
    UUID realTag = testData.createInterestTag("coffee", "Coffee", "drinks");

    String payload =
        jsonMapper.writeValueAsString(java.util.Map.of("interestIds", List.of(realTag.toString())));

    mockMvc
        .perform(
            put("/api/users/me/interests")
                .with(authSupport.userJwt(lurker))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("USER_002"));
  }
}
