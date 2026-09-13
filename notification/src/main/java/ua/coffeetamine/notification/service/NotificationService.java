package ua.coffeetamine.notification.service;

import java.util.UUID;

import org.springframework.data.domain.Pageable;

import ua.coffeetamine.api.notification.dto.Notification;
import ua.coffeetamine.api.notification.dto.NotificationListResponse;
import ua.coffeetamine.api.notification.dto.UnreadCountResponse;

public interface NotificationService {

  NotificationListResponse listMyNotifications(Boolean isRead, Pageable pageable);

  UnreadCountResponse getMyUnreadCount();

  Notification markRead(UUID notificationId);

  void markAllRead();
}
