package ua.coffeetamine.common.spi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cross-module read port for presence state. Other modules MUST depend on this interface instead of
 * importing {@code UserPresence} / {@code UserPresenceRepository} directly.
 */
public interface PresenceLookup {

  /** True iff the user has a presence row and its status is {@code READY}. */
  boolean isReady(UUID userId);

  /** Ready presence with location for the given user, or empty when not Ready / no location. */
  Optional<NearbyPresence> findReadyVisible(UUID userId);

  /**
   * Ready users with locations inside the lat/lng bounding box, excluding the caller. Bounds form a
   * rectangle (already meters-to-degrees-converted by the caller). Used to seed discovery's
   * compatibility pass.
   */
  List<NearbyPresence> findReadyCandidatesInBounds(
      UUID excludeUserId,
      double minLatitude,
      double maxLatitude,
      double minLongitude,
      double maxLongitude);
}
