package ua.coffeetamine.integration.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.integration.BaseIntegrationTest;
import ua.coffeetamine.integration.TestAuthSupport;
import ua.coffeetamine.notification.domain.model.NotificationType;
import ua.coffeetamine.notification.domain.repository.NotificationRepository;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The whole product loop in one committed run, driven entirely through HTTP: onboarding → presence
 * READY → discovery sees the peer → tap pin (enriched detail) → ping → mutual pong → match → async
 * notification on both inboxes. This is the only test that exercises the full cross-module stitch
 * (user ↔ interests ↔ mood-board ↔ presence ↔ discovery ↔ ping ↔ notification); the sibling {@code
 * *IT}s pin one module/invariant each.
 *
 * <p>{@code NEVER} opts out of {@link BaseIntegrationTest}'s rollback: each MockMvc call commits
 * its own request transaction, and the {@code @ApplicationModuleListener} that writes the
 * notification only fires AFTER the publishing transaction commits — a rolled-back test would never
 * observe it (same reasoning as {@code NotificationListenerIdempotencyIT}).
 *
 * <p>ponytail: committed rows are not cleaned up. Safe because this is a single test method and the
 * Testcontainers Postgres is fresh per build, so nothing else commits READY presence at these
 * coords. Add a truncate-between-classes extension only when a second committing flow test lands.
 */
@Transactional(propagation = Propagation.NEVER)
class HappyPathJourneyIT extends BaseIntegrationTest {

  private static final double LAT = 50.45;
  private static final double LNG = 30.52;

  @Autowired JsonMapper jsonMapper;
  @Autowired TestAuthSupport authSupport;
  @Autowired NotificationRepository notificationRepository;

  @Test
  @DisplayName("full loop: onboard → ready → discover → ping → match → notification (both inboxes)")
  void fullEncounterLoop() throws Exception {
    // Shared interest catalog — identical picks → compatibility 100. Random codes so the
    // committed rows can't collide with the unique InterestTag.code across runs.
    String s = UUID.randomUUID().toString().substring(0, 8);
    List<UUID> tags =
        List.of(
            testData.createInterestTag("coffee-" + s, "Coffee", "drinks"),
            testData.createInterestTag("books-" + s, "Books", "culture"));

    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();

    // 1. Both finish the onboarding wizard (profile + interests + 6 mood images, atomically).
    onboard(alice, "Alice", tags);
    onboard(bob, "Bob", tags);

    // 2. Both go READY at the same spot (server jitters ≤80m → still well within radius).
    goReady(alice);
    goReady(bob);

    // 3. Alice opens the map: Bob is a nearby pin with full compatibility.
    mockMvc
        .perform(
            get("/api/discovery/nearby")
                .param("radiusMeters", "1000")
                .param("minCompatibility", "10")
                .param("size", "100")
                .with(authSupport.userJwt(alice)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.content[?(@.userId=='" + bob + "')].compatibilityScore", contains(100)));

    // 4. Alice taps the pin → enriched aggregate (profile + interests + mood board + presence).
    mockMvc
        .perform(get("/api/discovery/users/{id}", bob).with(authSupport.userJwt(alice)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.userId").value(bob.toString()))
        .andExpect(jsonPath("$.moodBoard.images.length()").value(6))
        .andExpect(jsonPath("$.compatibilityScore").value(100));

    // 5. Alice pings Bob.
    UUID pingId = sendPing(alice, bob);

    // 6. Bob pings back (pong) → exactly one match, both see each other as peer.
    mockMvc
        .perform(post("/api/pings/{id}/pong", pingId).with(authSupport.userJwt(bob)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.peerUserId").value(alice.toString()));

    // 7. Async, durable: Bob got PING_RECEIVED + MATCH_CREATED, Alice got MATCH_CREATED.
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(
            () -> {
              var all = notificationRepository.findAll();
              assertThat(all).filteredOn(n -> match(n, bob, alice)).hasSize(1);
              assertThat(all).filteredOn(n -> match(n, alice, bob)).hasSize(1);
              assertThat(all)
                  .filteredOn(
                      n ->
                          n.getUserId().equals(bob)
                              && n.getType() == NotificationType.PING_RECEIVED
                              && n.getPeerUserId().equals(alice))
                  .hasSize(1);
            });

    // 8. The inbox endpoint actually serves the match notification to Bob.
    mockMvc
        .perform(get("/api/notifications").with(authSupport.userJwt(bob)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.content[?(@.type=='MATCH_CREATED' && @.peerUserId=='" + alice + "')]",
                hasSize(1)));
  }

  private boolean match(
      ua.coffeetamine.notification.domain.model.Notification n, UUID owner, UUID peer) {
    return n.getUserId().equals(owner)
        && n.getType() == NotificationType.MATCH_CREATED
        && n.getPeerUserId().equals(peer);
  }

  private void onboard(UUID user, String name, List<UUID> tags) throws Exception {
    Map<String, Object> body =
        Map.of("name", name, "interestIds", tags, "moodBoardImages", moodImages());
    mockMvc
        .perform(
            post("/api/users/me/onboarding")
                .with(authSupport.userJwt(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.onboardingCompleted").value(true));
  }

  private void goReady(UUID user) throws Exception {
    Map<String, Object> body = Map.of("status", "READY", "rawLatitude", LAT, "rawLongitude", LNG);
    mockMvc
        .perform(
            put("/api/presence/me")
                .with(authSupport.userJwt(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)))
        .andExpect(status().isOk());
  }

  private UUID sendPing(UUID from, UUID to) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/pings")
                    .with(authSupport.userJwt(from))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(Map.of("targetUserId", to))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(body, "$.id"));
  }

  private List<Map<String, Object>> moodImages() {
    return IntStream.range(0, 6)
        .mapToObj(
            i ->
                Map.<String, Object>of(
                    "unsplashPhotoId", "photo-" + i,
                    "imageUrl", "https://images.unsplash.com/photo-" + i,
                    "photographerName", "Photographer " + i,
                    "photographerProfileUrl", "https://unsplash.com/@photographer" + i,
                    "attributionUrl", "https://unsplash.com/photos/photo-" + i))
        .toList();
  }
}
