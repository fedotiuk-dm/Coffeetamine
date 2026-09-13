package ua.coffeetamine.notification.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.api.notification.dto.Notification;
import ua.coffeetamine.api.notification.dto.NotificationListResponse;
import ua.coffeetamine.api.notification.dto.UnreadCountResponse;
import ua.coffeetamine.common.exception.NotFoundException;
import ua.coffeetamine.common.security.SecurityUtils;
import ua.coffeetamine.notification.domain.repository.NotificationRepository;
import ua.coffeetamine.notification.mapper.NotificationMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

  private final NotificationRepository repository;
  private final NotificationMapper mapper;
  private final SecurityUtils securityUtils;

  @Override
  public NotificationListResponse listMyNotifications(Boolean isRead, Pageable pageable) {
    return mapper.toListResponse(
        repository.findOwned(securityUtils.getCurrentUserId(), isRead, pageable));
  }

  @Override
  public UnreadCountResponse getMyUnreadCount() {
    return mapper.toUnreadCountResponse(repository.countUnread(securityUtils.getCurrentUserId()));
  }

  @Override
  @Transactional
  public Notification markRead(UUID notificationId) {
    ua.coffeetamine.notification.domain.model.Notification entity =
        repository
            .findByIdAndUserId(notificationId, securityUtils.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("Notification", notificationId));
    if (!entity.isRead()) {
      mapper.markRead(Instant.now(), entity);
    }
    return mapper.toResponse(entity);
  }

  @Override
  @Transactional
  public void markAllRead() {
    repository.markAllRead(securityUtils.getCurrentUserId(), Instant.now());
  }
}
