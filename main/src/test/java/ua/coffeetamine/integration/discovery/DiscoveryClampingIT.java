package ua.coffeetamine.integration.discovery;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;

import ua.coffeetamine.integration.BaseIntegrationTest;
import ua.coffeetamine.integration.TestAuthSupport;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Locks down the §7 conjunctive filter and the server-side clamping behaviour. Caller-supplied
 * {@code radiusMeters} and {@code minCompatibility} are clamped against {@code
 * coffeetamine.discovery.*} — clients cannot bypass the floor with {@code minCompatibility=1}, nor
 * scan the whole planet with {@code radiusMeters=100000}.
 */
class DiscoveryClampingIT extends BaseIntegrationTest {

  // Centre point — Kyiv. Anchor for distance calculations.
  private static final double CALLER_LAT = 50.4501;
  private static final double CALLER_LNG = 30.5234;

  @Autowired private TestAuthSupport authSupport;

  private UUID caller;
  private UUID closeNeighbour;
  private UUID midDistanceUser;
  private UUID farUser;

  @BeforeEach
  void seedWorld() {
    caller = testData.createOnboardedUser("Caller");
    closeNeighbour = testData.createOnboardedUser("Close");
    midDistanceUser = testData.createOnboardedUser("Mid");
    farUser = testData.createOnboardedUser("Far");

    UUID sharedTag = testData.createInterestTag("coffee", "Coffee", "drinks");
    testData.selectInterests(caller, List.of(sharedTag));
    testData.selectInterests(closeNeighbour, List.of(sharedTag));
    testData.selectInterests(midDistanceUser, List.of(sharedTag));
    testData.selectInterests(farUser, List.of(sharedTag));

    testData.seedReadyPresence(caller, CALLER_LAT, CALLER_LNG);
    testData.seedReadyPresence(closeNeighbour, CALLER_LAT + 0.001, CALLER_LNG); // ~110m north
    testData.seedReadyPresence(midDistanceUser, CALLER_LAT + 0.01, CALLER_LNG); // ~1.1km north
    testData.seedReadyPresence(farUser, CALLER_LAT + 0.1, CALLER_LNG); // ~11km north
  }

  @Test
  @DisplayName("default radius 1km — returns close + mid neighbours, excludes far user")
  void defaultRadiusFiltersFarUsers() throws Exception {
    mockMvc
        .perform(
            get("/api/discovery/nearby")
                .param("radiusMeters", "1000")
                .with(authSupport.userJwt(caller)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.userId == '" + closeNeighbour + "')]").exists())
        .andExpect(jsonPath("$.content[?(@.userId == '" + midDistanceUser + "')]").doesNotExist())
        .andExpect(jsonPath("$.content[?(@.userId == '" + farUser + "')]").doesNotExist());
  }

  @Test
  @DisplayName("client tries radius=100000 — server clamps down to maxRadiusMeters (5000)")
  void clampsOverlyLargeRadius() throws Exception {
    // 11km user must still be excluded because 5000m ceiling clamps the requested 100000m.
    mockMvc
        .perform(
            get("/api/discovery/nearby")
                .param("radiusMeters", "100000")
                .with(authSupport.userJwt(caller)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.userId == '" + farUser + "')]").doesNotExist())
        .andExpect(jsonPath("$.content[?(@.userId == '" + midDistanceUser + "')]").exists());
  }

  @Test
  @DisplayName(
      "client tries minCompatibility=1 — server clamps up to floor (10) — still returns matches")
  void clampsBypassAttemptOnCompatibility() throws Exception {
    // All seeded users share the same tag → compat=100 → all pass even with floor.
    // This test asserts clamping doesn't accidentally hide legitimate matches.
    mockMvc
        .perform(
            get("/api/discovery/nearby")
                .param("radiusMeters", "5000")
                .param("minCompatibility", "1")
                .with(authSupport.userJwt(caller)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.content[*].compatibilityScore",
                Matchers.everyItem(Matchers.greaterThanOrEqualTo(10))));
  }

  @Test
  @DisplayName("caller NOT_READY — 409 PRES_001")
  void rejectsNotReadyCaller() throws Exception {
    UUID lurker = testData.createOnboardedUser("Lurker");
    testData.seedNotReadyPresence(lurker);

    mockMvc
        .perform(
            get("/api/discovery/nearby")
                .param("radiusMeters", "1000")
                .with(authSupport.userJwt(lurker)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("PRES_001"));
  }
}
