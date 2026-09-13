package ua.coffeetamine.notification.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.notification.domain.model.DevicePlatform;
import ua.coffeetamine.notification.domain.model.UserDevice;

@Repository
@Transactional(readOnly = true)
public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {

  /** Paginated view for the API. */
  Page<UserDevice> findByAppUserId(UUID appUserId, Pageable pageable);

  /** Full list for push fan-out. */
  List<UserDevice> findByAppUserId(UUID appUserId);

  Optional<UserDevice> findByAppUserIdAndPlatformAndToken(
      UUID appUserId, DevicePlatform platform, String token);

  Optional<UserDevice> findByIdAndAppUserId(UUID id, UUID appUserId);
}
