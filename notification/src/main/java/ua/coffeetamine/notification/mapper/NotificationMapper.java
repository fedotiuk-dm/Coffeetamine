package ua.coffeetamine.notification.mapper;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;

import ua.coffeetamine.api.notification.dto.Device;
import ua.coffeetamine.api.notification.dto.DeviceListResponse;
import ua.coffeetamine.api.notification.dto.Notification;
import ua.coffeetamine.api.notification.dto.NotificationListResponse;
import ua.coffeetamine.api.notification.dto.RegisterDeviceRequest;
import ua.coffeetamine.api.notification.dto.UnreadCountResponse;
import ua.coffeetamine.common.config.CentralMapperConfig;
import ua.coffeetamine.common.spi.UserProfileSummary;
import ua.coffeetamine.notification.domain.model.NotificationType;
import ua.coffeetamine.notification.domain.model.UserDevice;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralMapperConfig.class)
public interface NotificationMapper {

  @Mapping(target = "isRead", source = "read")
  Notification toResponse(ua.coffeetamine.notification.domain.model.Notification entity);

  NotificationListResponse toListResponse(
      Page<ua.coffeetamine.notification.domain.model.Notification> page);

  Device toResponse(UserDevice device);

  DeviceListResponse toDeviceListResponse(Page<UserDevice> page);

  @Mapping(target = "unread", source = "unread")
  UnreadCountResponse toUnreadCountResponse(Integer unread);

  ua.coffeetamine.notification.domain.model.DevicePlatform toPlatform(
      ua.coffeetamine.api.notification.dto.DevicePlatform platform);

  @BeanMapping(ignoreByDefault = true)
  @Mapping(target = "appUserId", source = "appUserId")
  @Mapping(target = "platform", source = "request.platform")
  @Mapping(target = "token", source = "request.token")
  @Mapping(target = "appVersion", source = "request.appVersion")
  UserDevice toNewDevice(UUID appUserId, RegisterDeviceRequest request);

  @BeanMapping(ignoreByDefault = true)
  @Mapping(target = "read", constant = "true")
  @Mapping(target = "readAt", source = "readAt")
  void markRead(
      Instant readAt, @MappingTarget ua.coffeetamine.notification.domain.model.Notification entity);

  @BeanMapping(ignoreByDefault = true)
  @Mapping(target = "userId", source = "userId")
  @Mapping(target = "type", source = "type")
  @Mapping(target = "peerUserId", source = "peer.userId")
  @Mapping(target = "peerName", source = "peer.name")
  @Mapping(target = "peerAvatarUrl", source = "peer.avatarUrl")
  @Mapping(target = "pingId", source = "pingId")
  @Mapping(target = "matchId", source = "matchId")
  ua.coffeetamine.notification.domain.model.Notification toNewNotification(
      UUID userId, NotificationType type, UserProfileSummary peer, UUID pingId, UUID matchId);
}
