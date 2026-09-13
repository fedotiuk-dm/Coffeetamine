package ua.coffeetamine.presence.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.presence.domain.model.PresenceState;
import ua.coffeetamine.presence.domain.model.UserPresence;

@Repository
@Transactional(readOnly = true)
public interface UserPresenceRepository extends JpaRepository<UserPresence, UUID> {

  @Query(
      """
      select presence
      from UserPresence presence
      where presence.userId <> :excludedUserId
        and presence.status = :status
        and presence.location is not null
        and presence.location.latitude between :minLatitude and :maxLatitude
        and presence.location.longitude between :minLongitude and :maxLongitude
      """)
  List<UserPresence> findVisibleCandidatesInBounds(
      UUID excludedUserId,
      PresenceState status,
      double minLatitude,
      double maxLatitude,
      double minLongitude,
      double maxLongitude);

  /**
   * Returns the user's presence row, creating a {@code NOT_READY} placeholder on first access.
   * Idempotent: the PK on {@code user_id} prevents duplicates under concurrent first-access.
   */
  default UserPresence findOrCreate(UUID userId) {
    return findById(userId)
        .orElseGet(
            () ->
                save(
                    UserPresence.builder().userId(userId).status(PresenceState.NOT_READY).build()));
  }
}
