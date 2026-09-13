package ua.coffeetamine.notification.domain.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.common.domain.repository.GenericSpecification;
import ua.coffeetamine.notification.domain.model.Notification;
import ua.coffeetamine.notification.domain.model.NotificationType;

@Repository
@Transactional(readOnly = true)
public interface NotificationRepository
    extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

  default Page<Notification> findOwned(UUID userId, Boolean isRead, Pageable pageable) {
    return findAll(
        NotificationSpec.owned(userId, isRead),
        GenericSpecification.withDefaultSort(pageable, NotificationSpec.DEFAULT_SORT));
  }

  Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

  @Query("select count(n) from Notification n where n.userId = :userId and n.read = false")
  int countUnread(UUID userId);

  @Modifying
  @Transactional
  @Query(
      """
      update Notification n
      set n.read = true, n.readAt = :readAt
      where n.userId = :userId and n.read = false
      """)
  void markAllRead(UUID userId, Instant readAt);

  boolean existsByUserIdAndTypeAndPingId(UUID userId, NotificationType type, UUID pingId);

  boolean existsByUserIdAndTypeAndMatchId(UUID userId, NotificationType type, UUID matchId);
}
