package ua.coffeetamine.ping.service;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import ua.coffeetamine.common.event.PingMatchedEvent;
import ua.coffeetamine.ping.domain.model.PingInteraction;
import ua.coffeetamine.ping.domain.model.PingState;

import lombok.RequiredArgsConstructor;

/**
 * Orchestrates ping state transitions. On a match it publishes {@link PingMatchedEvent}, delivered
 * async to notification by the Modulith event registry after the caller's transaction commits.
 * Withdrawals need no notification.
 */
@Service
@RequiredArgsConstructor
public class PingStatusManager {

  private final PingStatusRules rules;
  private final ApplicationEventPublisher eventPublisher;

  /** Idempotent. No-op on already-WITHDRAWN; throws on MATCHED via {@link PingStatusRules}. */
  public void withdraw(PingInteraction ping) {
    if (ping.getStatus() == PingState.WITHDRAWN) {
      return;
    }
    rules.validateTransition(ping.getStatus(), PingState.WITHDRAWN);
    ping.setStatus(PingState.WITHDRAWN);
  }

  /** Idempotent. No-op on already-MATCHED; throws on WITHDRAWN via {@link PingStatusRules}. */
  public void match(PingInteraction ping, UUID matchId) {
    if (ping.getStatus() == PingState.MATCHED) {
      return;
    }
    rules.validateTransition(ping.getStatus(), PingState.MATCHED);
    ping.setStatus(PingState.MATCHED);
    eventPublisher.publishEvent(
        new PingMatchedEvent(matchId, ping.getPairLowId(), ping.getPairHighId()));
  }
}
