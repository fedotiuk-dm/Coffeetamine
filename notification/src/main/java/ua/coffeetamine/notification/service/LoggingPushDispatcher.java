package ua.coffeetamine.notification.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import ua.coffeetamine.notification.domain.model.Notification;
import ua.coffeetamine.notification.domain.repository.UserDeviceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dev / test stub for push delivery. Looks up the recipient's devices and logs each token instead
 * of calling FCM / APNs. Real {@link PushDispatcher} implementations should provide a bean of the
 * same type — this stub steps aside via {@link ConditionalOnMissingBean}.
 */
@Component
@ConditionalOnMissingBean(value = PushDispatcher.class, ignored = LoggingPushDispatcher.class)
@RequiredArgsConstructor
@Slf4j
public class LoggingPushDispatcher implements PushDispatcher {

  private final UserDeviceRepository userDeviceRepository;

  @Override
  public void dispatch(Notification notification) {
    userDeviceRepository
        .findByAppUserId(notification.getUserId())
        .forEach(
            device ->
                log.info(
                    "[push:stub] {} → user={} type={} platform={} token={}",
                    notification.getId(),
                    notification.getUserId(),
                    notification.getType(),
                    device.getPlatform(),
                    device.getToken().substring(0, Math.min(12, device.getToken().length()))));
  }
}
