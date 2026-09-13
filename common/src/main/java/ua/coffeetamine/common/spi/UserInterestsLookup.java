package ua.coffeetamine.common.spi;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-module read port for user interest selections. Discovery uses {@link #interestIdsFor} for
 * compatibility scoring (set overlap) and {@link #orderedInterestsFor} to display the full tag list
 * on the detail view.
 */
public interface UserInterestsLookup {

  /** Selected interest tag IDs for one user — used for compatibility-score set overlap. */
  Set<UUID> interestIdsFor(UUID userId);

  /** Batched id-set lookup — single SQL round-trip. Empty entry if the user has no selections. */
  Map<UUID, Set<UUID>> interestIdsFor(Collection<UUID> userIds);

  /** Ordered interest summaries for one user — preserves the order set by the last replace call. */
  List<InterestSummary> orderedInterestsFor(UUID userId);
}
