package ua.coffeetamine.notification.service;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.api.notification.dto.Device;
import ua.coffeetamine.api.notification.dto.DeviceListResponse;
import ua.coffeetamine.api.notification.dto.RegisterDeviceRequest;
import ua.coffeetamine.common.exception.NotFoundException;
import ua.coffeetamine.common.security.SecurityUtils;
import ua.coffeetamine.notification.domain.model.DevicePlatform;
import ua.coffeetamine.notification.domain.model.UserDevice;
import ua.coffeetamine.notification.domain.repository.UserDeviceRepository;
import ua.coffeetamine.notification.mapper.NotificationMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceServiceImpl implements DeviceService {

  private final UserDeviceRepository repository;
  private final NotificationMapper mapper;
  private final SecurityUtils securityUtils;

  @Override
  public DeviceListResponse listMyDevices(Pageable pageable) {
    return mapper.toDeviceListResponse(
        repository.findByAppUserId(securityUtils.getCurrentUserId(), pageable));
  }

  /**
   * Idempotent register: re-registration of the same (platform, token) refreshes appVersion on the
   * existing row. The unique (app_user_id, platform, token) makes a duplicate row impossible; a
   * rare concurrent double-submit of the same token loses to that constraint and surfaces a 409,
   * which the client simply retries.
   */
  @Override
  @Transactional
  public Device registerDevice(RegisterDeviceRequest request) {
    UUID userId = securityUtils.getCurrentUserId();
    DevicePlatform platform = mapper.toPlatform(request.getPlatform());
    UserDevice device =
        repository
            .findByAppUserIdAndPlatformAndToken(userId, platform, request.getToken())
            .orElseGet(() -> mapper.toNewDevice(userId, request));
    device.setAppVersion(request.getAppVersion());
    return mapper.toResponse(repository.save(device));
  }

  @Override
  @Transactional
  public void unregisterDevice(UUID deviceId) {
    UserDevice device =
        repository
            .findByIdAndAppUserId(deviceId, securityUtils.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("Device", deviceId));
    repository.delete(device);
  }
}
