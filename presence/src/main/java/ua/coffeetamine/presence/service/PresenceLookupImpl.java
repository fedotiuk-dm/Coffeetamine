package ua.coffeetamine.presence.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.common.spi.NearbyPresence;
import ua.coffeetamine.common.spi.PresenceLookup;
import ua.coffeetamine.common.spi.PresenceStatus;
import ua.coffeetamine.presence.domain.model.JitteredLocation;
import ua.coffeetamine.presence.domain.model.PresenceState;
import ua.coffeetamine.presence.domain.model.UserPresence;
import ua.coffeetamine.presence.domain.repository.UserPresenceRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class PresenceLookupImpl implements PresenceLookup {

  private final UserPresenceRepository repository;

  @Override
  public boolean isReady(UUID userId) {
    return repository
        .findById(userId)
        .map(presence -> presence.getStatus() == PresenceState.READY)
        .orElse(false);
  }

  @Override
  public Optional<NearbyPresence> findReadyVisible(UUID userId) {
    return repository
        .findById(userId)
        .filter(p -> p.getStatus() == PresenceState.READY)
        .filter(p -> p.getLocation() != null)
        .map(PresenceLookupImpl::toNearby);
  }

  @Override
  public List<NearbyPresence> findReadyCandidatesInBounds(
      UUID excludeUserId,
      double minLatitude,
      double maxLatitude,
      double minLongitude,
      double maxLongitude) {
    return repository
        .findVisibleCandidatesInBounds(
            excludeUserId,
            PresenceState.READY,
            minLatitude,
            maxLatitude,
            minLongitude,
            maxLongitude)
        .stream()
        .map(PresenceLookupImpl::toNearby)
        .toList();
  }

  private static NearbyPresence toNearby(UserPresence presence) {
    JitteredLocation location = presence.getLocation();
    return new NearbyPresence(
        presence.getUserId(),
        PresenceStatus.valueOf(presence.getStatus().name()),
        location.latitude(),
        location.longitude(),
        presence.getMood(),
        presence.getUpdatedAt());
  }
}
