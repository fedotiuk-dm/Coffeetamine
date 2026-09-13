package ua.coffeetamine.integration.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import ua.coffeetamine.integration.BaseIntegrationTest;
import ua.coffeetamine.integration.TestAuthSupport;
import ua.coffeetamine.presence.domain.repository.UserPresenceRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Locks down PRODUCT_CONCEPT §10: raw GPS coordinates submitted via {@code PUT /api/presence/me}
 * are never persisted or echoed back. Only jittered coordinates leave the server.
 */
class PresenceJitterIT extends BaseIntegrationTest {

  // Centre of Kyiv — easy to eyeball if a test fails.
  private static final double RAW_LAT = 50.4501;
  private static final double RAW_LNG = 30.5234;

  @Autowired JsonMapper jsonMapper;
  @Autowired TestAuthSupport authSupport;
  @Autowired UserPresenceRepository presenceRepository;

  @Test
  @DisplayName(
      "raw GPS is jittered before persistence — response coords are within ~80m but not exact")
  void jittersRawGpsBeforeReturning() throws Exception {
    UUID userId = testData.createOnboardedUser("Dima");

    String payload =
        jsonMapper.writeValueAsString(
            java.util.Map.of(
                "status", "READY",
                "rawLatitude", RAW_LAT,
                "rawLongitude", RAW_LNG));

    MvcResult result =
        mockMvc
            .perform(
                put("/api/presence/me")
                    .with(authSupport.userJwt(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READY"))
            .andReturn();

    Map<String, Object> responseBody =
        jsonMapper.readValue(
            result.getResponse().getContentAsByteArray(), new TypeReference<>() {});
    @SuppressWarnings("unchecked")
    Map<String, Object> location = (Map<String, Object>) responseBody.get("location");

    double returnedLat = ((Number) location.get("latitude")).doubleValue();
    double returnedLng = ((Number) location.get("longitude")).doubleValue();

    // Persisted coords match the response (server returns what it stored).
    var presence = presenceRepository.findById(userId).orElseThrow();
    assertThat(presence.getLocation().latitude()).isEqualTo(returnedLat);
    assertThat(presence.getLocation().longitude()).isEqualTo(returnedLng);

    // Jitter is non-zero (raw and returned must differ) and within the configured ~80m radius.
    // Distance is approximated via great-circle on the equator-scale (good enough for assertion
    // bounds; the test only needs to prove the offset is in the expected band).
    double distanceMeters = greatCircleMeters(returnedLat, returnedLng);
    assertThat(distanceMeters)
        .as("jittered offset within (0, 85]m — must be positive and within max jitter radius")
        .isPositive()
        .isLessThan(85.0);
  }

  @Test
  @DisplayName("re-jitter on second PUT: same raw GPS produces different stored coords")
  void rejittersOnSecondUpdate() throws Exception {
    UUID userId = testData.createOnboardedUser("Dima");
    String payload =
        jsonMapper.writeValueAsString(
            java.util.Map.of(
                "status", "READY",
                "rawLatitude", RAW_LAT,
                "rawLongitude", RAW_LNG));

    mockMvc
        .perform(
            put("/api/presence/me")
                .with(authSupport.userJwt(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk());
    var firstCoords =
        new double[] {
          presenceRepository.findById(userId).orElseThrow().getLocation().latitude(),
          presenceRepository.findById(userId).orElseThrow().getLocation().longitude()
        };

    mockMvc
        .perform(
            put("/api/presence/me")
                .with(authSupport.userJwt(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk());
    var secondCoords =
        new double[] {
          presenceRepository.findById(userId).orElseThrow().getLocation().latitude(),
          presenceRepository.findById(userId).orElseThrow().getLocation().longitude()
        };

    // Two independent jitters of the same raw input must (with overwhelming probability) differ.
    assertThat(firstCoords)
        .as("jitter must be re-randomised on each update — prevents triangulation")
        .isNotEqualTo(secondCoords);
  }

  private static double greatCircleMeters(double lat2, double lng2) {
    final double earthRadius = 6_371_000.0;
    double dLat = Math.toRadians(lat2 - PresenceJitterIT.RAW_LAT);
    double dLng = Math.toRadians(lng2 - PresenceJitterIT.RAW_LNG);
    double a =
        Math.pow(Math.sin(dLat / 2.0), 2)
            + Math.cos(Math.toRadians(PresenceJitterIT.RAW_LAT))
                * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(dLng / 2.0), 2);
    return 2.0 * earthRadius * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
  }
}
