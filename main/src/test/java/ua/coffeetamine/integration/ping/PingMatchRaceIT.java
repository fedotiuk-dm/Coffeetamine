package ua.coffeetamine.integration.ping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.integration.BaseIntegrationTest;
import ua.coffeetamine.integration.TestAuthSupport;
import ua.coffeetamine.ping.domain.repository.UserMatchRepository;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Regression for the concurrent-pong race. When the same PENDING ping is ponged by several requests
 * at once, the pessimistic row lock in {@code pongPing} ({@code findByIdForUpdate}) serializes
 * them: the first creates the {@code UserMatch}, the rest observe the ping already MATCHED and
 * resolve the same match. Every request returns 200 and exactly one match exists for the unordered
 * pair. Before the lock, the losers raced into the {@code uk_user_matches_pair} index, aborting
 * their transaction — and the catch-block re-read on that aborted connection surfaced as a 500.
 *
 * <p>Committing flow → opts out of the {@link BaseIntegrationTest} rollback (the pong threads run
 * in their own transactions and could never see a still-uncommitted ping). Fresh random users per
 * run keep the committed rows inert for siblings.
 */
@Transactional(propagation = Propagation.NEVER)
class PingMatchRaceIT extends BaseIntegrationTest {

  private static final double LAT = 50.45;
  private static final double LNG = 30.52;
  private static final int PONGERS = 4;

  @Autowired JsonMapper jsonMapper;
  @Autowired TestAuthSupport authSupport;
  @Autowired UserMatchRepository matchRepository;

  @Test
  @DisplayName("concurrent pong on one ping → exactly one match, every request 200 (no 500)")
  void concurrentPongCreatesOneMatch() throws Exception {
    String s = UUID.randomUUID().toString().substring(0, 8);
    List<UUID> tags = List.of(testData.createInterestTag("coffee-" + s, "Coffee", "drinks"));

    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    onboard(alice, tags);
    onboard(bob, tags);
    goReady(bob);
    UUID pingId = sendPing(alice, bob);

    CyclicBarrier startGate = new CyclicBarrier(PONGERS);
    ExecutorService pool = Executors.newFixedThreadPool(PONGERS);
    try {
      List<Integer> statuses =
          IntStream.range(0, PONGERS)
              .mapToObj(
                  _ ->
                      CompletableFuture.supplyAsync(
                          () -> pongAtBarrier(startGate, bob, pingId), pool))
              .toList()
              .stream()
              .map(CompletableFuture::join)
              .toList();

      assertThat(statuses)
          .as("every concurrent pong returns 200, none aborts to 500")
          .containsOnly(200);
      assertThat(matchRepository.findByPair(alice, bob))
          .as("exactly one match for the unordered pair")
          .isPresent();
    } finally {
      pool.shutdownNow();
      assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  private int pongAtBarrier(CyclicBarrier gate, UUID ponger, UUID pingId) {
    try {
      gate.await(10, TimeUnit.SECONDS);
      return mockMvc
          .perform(post("/api/pings/{id}/pong", pingId).with(authSupport.userJwt(ponger)))
          .andReturn()
          .getResponse()
          .getStatus();
    } catch (Exception e) {
      throw new IllegalStateException("concurrent pong failed", e);
    }
  }

  private void onboard(UUID user, List<UUID> tags) throws Exception {
    Map<String, Object> body =
        Map.of("name", "User", "interestIds", tags, "moodBoardImages", moodImages());
    mockMvc
        .perform(
            post("/api/users/me/onboarding")
                .with(authSupport.userJwt(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)))
        .andExpect(status().isOk());
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
