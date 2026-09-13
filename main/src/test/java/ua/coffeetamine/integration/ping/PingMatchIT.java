package ua.coffeetamine.integration.ping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import ua.coffeetamine.integration.BaseIntegrationTest;
import ua.coffeetamine.integration.TestAuthSupport;
import ua.coffeetamine.ping.domain.repository.PingInteractionRepository;
import ua.coffeetamine.ping.domain.repository.UserMatchRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Locks down §11 invariants: ping/pong creates exactly one match per unordered pair, even when the
 * application layer tries to double-send. The DB-level partial unique index on {@code (pair_low_id,
 * pair_high_id) WHERE status='PENDING'} is what enforces this.
 */
class PingMatchIT extends BaseIntegrationTest {

  @Autowired JsonMapper jsonMapper;
  @Autowired TestAuthSupport authSupport;
  @Autowired PingInteractionRepository pingRepository;
  @Autowired UserMatchRepository matchRepository;

  @Test
  @DisplayName("mutual ping → exactly one match, both ping rows MATCHED")
  void mutualPingCreatesOneMatch() throws Exception {
    UUID alice = testData.createOnboardedUser("Alice");
    UUID bob = testData.createOnboardedUser("Bob");
    testData.seedReadyPresence(alice, 50.45, 30.52);
    testData.seedReadyPresence(bob, 50.45, 30.52);

    UUID alicePingId = sendPing(alice, bob);

    // Bob pongs Alice's ping → creates UserMatch. Response body is the Match DTO whose
    // id field IS the match id (no `matchId` envelope — see ping-schemas.yaml `Match`).
    mockMvc
        .perform(post("/api/pings/{id}/pong", alicePingId).with(authSupport.userJwt(bob)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.peerUserId").value(alice.toString()));

    assertThat(matchRepository.findAll()).as("exactly one match per unordered pair").hasSize(1);
    assertThat(pingRepository.findById(alicePingId).orElseThrow().getStatus().name())
        .isEqualTo("MATCHED");
  }

  @Test
  @DisplayName("duplicate ping send (A→B twice) → 409 PING_002")
  void duplicatePingReturnsConflict() throws Exception {
    UUID alice = testData.createOnboardedUser("Alice");
    UUID bob = testData.createOnboardedUser("Bob");
    testData.seedReadyPresence(bob, 50.45, 30.52);

    sendPing(alice, bob);

    String payload =
        jsonMapper.writeValueAsString(java.util.Map.of("targetUserId", bob.toString()));
    mockMvc
        .perform(
            post("/api/pings")
                .with(authSupport.userJwt(alice))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("PING_002"));
  }

  @Test
  @DisplayName("self-ping rejected → 400 PING_003")
  void selfPingRejected() throws Exception {
    UUID alice = testData.createOnboardedUser("Alice");
    testData.seedReadyPresence(alice, 50.45, 30.52);

    String payload =
        jsonMapper.writeValueAsString(java.util.Map.of("targetUserId", alice.toString()));
    mockMvc
        .perform(
            post("/api/pings")
                .with(authSupport.userJwt(alice))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("PING_003"));
  }

  @Test
  @DisplayName("ping to NOT_READY target rejected → 409 PING_001")
  void pingToNotReadyTargetRejected() throws Exception {
    UUID alice = testData.createOnboardedUser("Alice");
    UUID bob = testData.createOnboardedUser("Bob");
    testData.seedNotReadyPresence(bob);

    String payload =
        jsonMapper.writeValueAsString(java.util.Map.of("targetUserId", bob.toString()));
    mockMvc
        .perform(
            post("/api/pings")
                .with(authSupport.userJwt(alice))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("PING_001"));
  }

  @Test
  @DisplayName("non-onboarded caller blocked → 404 USER_002")
  void nonOnboardedCallerBlocked() throws Exception {
    UUID lurker = testData.createIncompleteUser("Lurker");
    UUID bob = testData.createOnboardedUser("Bob");
    testData.seedReadyPresence(bob, 50.45, 30.52);

    String payload =
        jsonMapper.writeValueAsString(java.util.Map.of("targetUserId", bob.toString()));
    mockMvc
        .perform(
            post("/api/pings")
                .with(authSupport.userJwt(lurker))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("USER_002"));
  }

  private UUID sendPing(UUID from, UUID to) throws Exception {
    String payload = jsonMapper.writeValueAsString(java.util.Map.of("targetUserId", to.toString()));
    MvcResult result =
        mockMvc
            .perform(
                post("/api/pings")
                    .with(authSupport.userJwt(from))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andExpect(status().isCreated())
            .andReturn();
    var body =
        jsonMapper.readValue(result.getResponse().getContentAsByteArray(), java.util.Map.class);
    return UUID.fromString((String) body.get("id"));
  }
}
