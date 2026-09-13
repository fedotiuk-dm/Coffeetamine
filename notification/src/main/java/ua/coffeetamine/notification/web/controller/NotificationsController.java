package ua.coffeetamine.notification.web.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import ua.coffeetamine.api.notification.NotificationsApi;
import ua.coffeetamine.api.notification.dto.Notification;
import ua.coffeetamine.api.notification.dto.NotificationListResponse;
import ua.coffeetamine.api.notification.dto.UnreadCountResponse;
import ua.coffeetamine.common.security.RequiresAuthenticated;
import ua.coffeetamine.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequiresAuthenticated
public class NotificationsController implements NotificationsApi {

  private final NotificationService service;

  @Override
  public ResponseEntity<NotificationListResponse> listNotifications(
      Boolean isRead, Pageable pageable) {
    return ResponseEntity.ok(service.listMyNotifications(isRead, pageable));
  }

  @Override
  public ResponseEntity<UnreadCountResponse> getUnreadNotificationCount() {
    return ResponseEntity.ok(service.getMyUnreadCount());
  }

  @Override
  public ResponseEntity<Notification> markNotificationRead(UUID notificationId) {
    return ResponseEntity.ok(service.markRead(notificationId));
  }

  @Override
  public ResponseEntity<Void> markAllNotificationsRead() {
    service.markAllRead();
    return ResponseEntity.noContent().build();
  }
}
