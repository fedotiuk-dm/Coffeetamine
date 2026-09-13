package ua.coffeetamine.common.spi;

import java.util.List;
import java.util.UUID;

/**
 * Cross-module write port for user interest selections. Lets non-interests modules (currently
 * {@code user} during onboarding) atomically replace a user's selections inside an outer
 * transaction — without depending on internal services or repositories.
 */
public interface UserInterestsWriter {

  /**
   * Atomically replace the user's current selections with the given tag IDs. Order is preserved.
   * The caller is expected to wrap this in its own {@code @Transactional} so failures elsewhere in
   * the onboarding chain roll back the selection write together with the rest.
   *
   * <p>Throws {@code NotFoundException(INTERESTS_TAG_UNKNOWN)} if any ID is unknown or inactive;
   * {@code BadRequestException} if duplicates are present.
   */
  void replaceSelections(UUID userId, List<UUID> interestIds);
}
