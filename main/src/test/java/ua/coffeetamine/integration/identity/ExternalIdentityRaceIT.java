package ua.coffeetamine.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import ua.coffeetamine.common.identity.CachingUserIdentityResolver;
import ua.coffeetamine.common.identity.ExternalIdentityRepository;
import ua.coffeetamine.common.identity.UserIdentityResolver;
import ua.coffeetamine.integration.BaseIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Concurrent first-login probe for {@link CachingUserIdentityResolver}: many threads call {@code
 * resolve(iss, sub)} with the same {@code (iss, sub)} tuple before any of them has had time to
 * write a row. Exactly one inserter wins the unique constraint; every other caller catches {@code
 * DataIntegrityViolationException}, re-reads the winning row, and returns the same {@code
 * app_user_id}.
 *
 * <p>This covers the propagation + constraint behaviour that the unit-test version can only mock:
 *
 * <ul>
 *   <li>{@code ExternalIdentityProvisioner.@Transactional(REQUIRES_NEW)} really opens an
 *       independent transaction, so the loser's rollback does not poison the outer (test) tx;
 *   <li>the {@code (issuer, subject)} unique index in the migrated schema truly throws {@code
 *       DataIntegrityViolationException} under concurrent inserts;
 *   <li>the catch-block re-read finds the winning row even when invoked from the loser thread.
 * </ul>
 *
 * <p>The class-level {@code @Transactional} on {@link BaseIntegrationTest} keeps test-side reads /
 * fixture writes isolated — but the {@code REQUIRES_NEW} inside the provisioner is intentionally
 * independent of that wrapper, so the winning row really does commit and survives the outer test
 * rollback. Each test method picks a fresh random {@code subject}, so the committed rows from
 * previous runs are inert (different natural key) and require no cleanup.
 */
class ExternalIdentityRaceIT extends BaseIntegrationTest {

  private static final String ISSUER = "https://race.example.com";
  private static final int CALLERS = 8;
  private static final long BARRIER_TIMEOUT_SECONDS = 10;

  @Autowired UserIdentityResolver resolver;
  @Autowired ExternalIdentityRepository identityRepository;
  @Autowired CacheManager cacheManager;

  private String subject;

  @BeforeEach
  void freshIdentity() {
    subject = "race-" + UUID.randomUUID();
    Cache cache = cacheManager.getCache(CachingUserIdentityResolver.CACHE_NAME);
    if (cache != null) {
      cache.clear();
    }
  }

  @Test
  @DisplayName("concurrent first-login resolves to a single app_user_id; one row in the DB")
  void concurrent_provisioning_serialises_on_unique_constraint() throws Exception {
    CyclicBarrier startGate = new CyclicBarrier(CALLERS);
    ExecutorService pool = Executors.newFixedThreadPool(CALLERS);
    try {
      List<CompletableFuture<UUID>> futures =
          IntStream.range(0, CALLERS)
              .mapToObj(_ -> CompletableFuture.supplyAsync(() -> resolveAtBarrier(startGate), pool))
              .toList();

      List<UUID> distinctResolved =
          futures.stream().map(CompletableFuture::join).distinct().toList();

      assertThat(distinctResolved).as("every caller converges on the same app_user_id").hasSize(1);
      assertThat(identityRepository.findByIssuerAndSubject(ISSUER, subject))
          .as("exactly one external_identities row was committed")
          .hasValueSatisfying(
              row -> assertThat(row.getAppUserId()).isEqualTo(distinctResolved.getFirst()));
    } finally {
      pool.shutdownNow();
      assertThat(pool.awaitTermination(5, TimeUnit.SECONDS))
          .as("worker pool terminated after shutdownNow")
          .isTrue();
    }
  }

  private UUID resolveAtBarrier(CyclicBarrier barrier) {
    try {
      barrier.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new IllegalStateException("race-start barrier failed", e);
    }
    return resolver.resolve(ISSUER, subject, "u@example.com");
  }
}
