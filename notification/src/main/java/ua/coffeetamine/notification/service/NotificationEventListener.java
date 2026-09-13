package ua.coffeetamine.notification.service;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ua.coffeetamine.common.event.PingMatchedEvent;
import ua.coffeetamine.common.event.PingSentEvent;
import ua.coffeetamine.common.spi.UserProfileLookup;
import ua.coffeetamine.common.spi.UserProfileSummary;
import ua.coffeetamine.notification.domain.model.Notification;
import ua.coffeetamine.notification.domain.model.NotificationType;
import ua.coffeetamine.notification.domain.repository.NotificationRepository;
import ua.coffeetamine.notification.mapper.NotificationMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reacts to ping domain events (async, durable via the Modulith event registry) and writes in-app
 * notifications + best-effort push. Peer name/avatar come from the {@code common} {@link
 * UserProfileLookup} SPI (a synchronous read, impl in the user module) — no profile copy lives
 * here. Each handler serializes concurrent deliveries for the same notification key on a
 * transaction-scoped Postgres advisory lock, then an {@code existsBy} check skips the duplicate —
 * so at-least-once redelivery yields exactly one row without tripping the {@code
 * uk_notifications_*} unique index (which stays as the DB-level backstop).
 */
@Component
@RequiredArgsConstructor
@Slf4j
class NotificationEventListener {

  private final NotificationRepository notificationRepository;
  private final UserProfileLookup userProfileLookup;
  private final PushDispatcher pushDispatcher;
  private final NotificationMapper mapper;

  @PersistenceContext private EntityManager entityManager;

  @ApplicationModuleListener
  void onPingSent(PingSentEvent event) {
    lockOn(event.toUserId(), event.pingId());
    if (notificationRepository.existsByUserIdAndTypeAndPingId(
        event.toUserId(), NotificationType.PING_RECEIVED, event.pingId())) {
      return;
    }
    UserProfileSummary sender = requirePeer(event.fromUserId());
    persistAndPush(
        mapper.toNewNotification(
            event.toUserId(), NotificationType.PING_RECEIVED, sender, event.pingId(), null));
  }

  @ApplicationModuleListener
  void onPingMatched(PingMatchedEvent event) {
    // Each participant sees the other as the peer.
    notifyMatch(event.participantLowId(), event.participantHighId(), event.matchId());
    notifyMatch(event.participantHighId(), event.participantLowId(), event.matchId());
  }

  private void notifyMatch(UUID recipient, UUID peerId, UUID matchId) {
    lockOn(recipient, matchId);
    if (notificationRepository.existsByUserIdAndTypeAndMatchId(
        recipient, NotificationType.MATCH_CREATED, matchId)) {
      return;
    }
    UserProfileSummary peer = requirePeer(peerId);
    persistAndPush(
        mapper.toNewNotification(recipient, NotificationType.MATCH_CREATED, peer, null, matchId));
  }

  /**
   * Serialize concurrent deliveries that target the same notification key on a transaction-scoped
   * Postgres advisory lock, so the {@code existsBy} check below can't race a parallel insert. Both
   * 128-bit UUIDs are folded into the single 64-bit key {@code pg_advisory_xact_lock} accepts.
   */
  private void lockOn(UUID a, UUID b) {
    long key =
        a.getMostSignificantBits()
            ^ a.getLeastSignificantBits()
            ^ b.getMostSignificantBits()
            ^ b.getLeastSignificantBits();
    entityManager
        .createNativeQuery("SELECT pg_advisory_xact_lock(:key)")
        .setParameter("key", key)
        .getSingleResult();
  }

  private void persistAndPush(Notification draft) {
    Notification saved = notificationRepository.save(draft);
    // Push after commit so the advisory lock + DB connection aren't held across the external send.
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            safePush(saved);
          }
        });
  }

  private UserProfileSummary requirePeer(UUID userId) {
    return userProfileLookup
        .findById(userId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "User profile " + userId + " missing — refusing to create notification"));
  }

  private void safePush(Notification notification) {
    try {
      pushDispatcher.dispatch(notification);
    } catch (RuntimeException ex) {
      log.warn("Push dispatch failed for {} — in-app row stays", notification.getId(), ex);
    }
  }
}
