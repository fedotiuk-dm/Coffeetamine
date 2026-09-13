package ua.coffeetamine.common.spi;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cross-module read port for user profile summaries. Other modules MUST depend on this interface
 * instead of importing {@code UserProfile} / {@code UserProfileRepository} directly.
 */
public interface UserProfileLookup {

  Optional<UserProfileSummary> findById(UUID userId);

  /** Batch fetch — single SQL round-trip. Missing IDs are simply absent from the map. */
  Map<UUID, UserProfileSummary> findAllByIds(Collection<UUID> userIds);
}
