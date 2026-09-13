package ua.coffeetamine.integration.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.common.event.PingSentEvent;
import ua.coffeetamine.integration.BaseIntegrationTest;
import ua.coffeetamine.notification.domain.repository.NotificationRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Locks down the consumer-side idempotency invariant. The notification listener is idempotent via a
 * Spring Data {@code existsBy} check on the notification's natural key (recipient + type + ping id)
 * — the same ping event delivered twice must produce exactly one {@code Notification} row.
 *
 * <p>Publishes the domain event in two separate committed transactions: Spring Modulith delivers
 * {@code @ApplicationModuleListener}s only after the publishing transaction commits. Re-publishing
 * the same event mimics the registry's at-least-once redelivery; the listener must dedupe.
 */
// Opt out of the class-level @Transactional from BaseIntegrationTest: the
// @ApplicationModuleListener
// fires only AFTER a publishing transaction commits and runs async on its own connection. A
// surrounding test transaction would never commit (rolled back at test end), so the listener would
// never observe the event. NEVER means "do not run inside a transaction"; each publish below opens
// and commits its own tx via the TransactionTemplate. Uniqueness comes from a random ping id.
@Transactional(propagation = Propagation.NEVER)
class NotificationListenerIdempotencyIT extends BaseIntegrationTest {

  @Autowired ApplicationEventPublisher eventPublisher;
  @Autowired NotificationRepository notificationRepository;

  @Test
  @DisplayName("redelivered event for same ping → one Notification row")
  void duplicateEventProducesSingleNotification() {
    UUID sender = testData.createOnboardedUser("Alice");
    UUID recipient = testData.createOnboardedUser("Bob");

    UUID pingId = UUID.randomUUID();
    PingSentEvent event = new PingSentEvent(pingId, sender, recipient);

    // Publish the SAME event twice in separate committed transactions — Modulith delivery is
    // at-least-once, and the listener must dedupe via its existsBy check.
    publishInOwnTransaction(event);
    publishInOwnTransaction(event);

    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(
            () ->
                assertThat(notificationRepository.findAll())
                    .filteredOn(n -> n.getUserId().equals(recipient))
                    .hasSize(1));
  }

  private void publishInOwnTransaction(PingSentEvent event) {
    tx.executeWithoutResult(_ -> eventPublisher.publishEvent(event));
  }
}
