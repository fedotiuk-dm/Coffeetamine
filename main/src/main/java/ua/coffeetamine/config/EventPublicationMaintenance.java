package ua.coffeetamine.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Housekeeping for the Spring Modulith event publication registry.
 *
 * <p>{@link #resubmitIncompletePublications()} resubmits publications that were registered but
 * never completed (a listener threw, or the application stopped mid-processing). Together with
 * {@code spring.modulith.events.republish-outstanding-events-on-restart} this gives at-least-once
 * in-process delivery without the previous custom outbox poller. Each sweep is bounded by {@code
 * maxInFlight} so a large stuck backlog drains incrementally instead of re-firing the whole set
 * every interval.
 *
 * <p>{@link #purgeArchivedPublications()} prunes the {@code event_publication_archive} table. With
 * {@code completion-mode: archive} successfully completed publications are moved there for
 * inspection rather than deleted, so the archive needs periodic trimming to stay bounded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublicationMaintenance {

  private final IncompleteEventPublications incompletePublications;
  private final CompletedEventPublications completedPublications;

  @Value("${app.modulith.events.archive-retention-days:14}")
  private int archiveRetentionDays;

  @Value("${app.modulith.events.resubmit-max-in-flight:100}")
  private int resubmitMaxInFlight;

  /**
   * Stop resubmitting a publication once it has failed this many times — a poison event (e.g. a
   * permanently-missing peer profile) is left incomplete in the registry for inspection instead of
   * being re-fired forever. Replaces the old RabbitMQ DLX + max-retries cap.
   */
  @Value("${app.modulith.events.resubmit-max-attempts:10}")
  private int resubmitMaxAttempts;

  /** Only resubmit publications older than this, to avoid racing an in-flight async listener. */
  @Value("${app.modulith.events.resubmit-min-age:5m}")
  private Duration resubmitMinAge;

  @Scheduled(fixedDelayString = "${app.modulith.events.resubmit-interval-ms:300000}")
  void resubmitIncompletePublications() {
    incompletePublications.resubmitIncompletePublications(
        ResubmissionOptions.defaults()
            .withMinAge(resubmitMinAge)
            .withMaxInFlight(resubmitMaxInFlight)
            .withFilter(publication -> publication.getCompletionAttempts() < resubmitMaxAttempts));
  }

  @Scheduled(cron = "${app.modulith.events.archive-cleanup-cron:0 0 2 * * *}")
  void purgeArchivedPublications() {
    completedPublications.deletePublicationsOlderThan(Duration.ofDays(archiveRetentionDays));
  }
}
