package ua.coffeetamine.ping.service;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import ua.coffeetamine.common.exception.ConflictException;
import ua.coffeetamine.common.exception.ErrorCode;
import ua.coffeetamine.ping.domain.model.PingState;

/**
 * Declarative state-machine for ping interactions. Centralises legal transitions so service code
 * can stay free of {@code if (status == X) throw} cascades.
 */
@Component
public class PingStatusRules {

  private final Map<PingState, Set<PingState>> allowedTransitions =
      Map.of(
          PingState.PENDING, Set.of(PingState.MATCHED, PingState.WITHDRAWN),
          PingState.MATCHED, Set.of(),
          PingState.WITHDRAWN, Set.of());

  public boolean canTransition(PingState from, PingState to) {
    return allowedTransitions.getOrDefault(from, Set.of()).contains(to);
  }

  public void validateTransition(PingState from, PingState to) {
    if (!canTransition(from, to)) {
      throw new ConflictException(
          ErrorCode.PING_INVALID_TRANSITION, "Ping cannot transition from " + from + " to " + to);
    }
  }
}
