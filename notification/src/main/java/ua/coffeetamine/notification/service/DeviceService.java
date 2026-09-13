package ua.coffeetamine.notification.service;

import java.util.UUID;

import org.springframework.data.domain.Pageable;

import ua.coffeetamine.api.notification.dto.Device;
import ua.coffeetamine.api.notification.dto.DeviceListResponse;
import ua.coffeetamine.api.notification.dto.RegisterDeviceRequest;

public interface DeviceService {

  DeviceListResponse listMyDevices(Pageable pageable);

  Device registerDevice(RegisterDeviceRequest request);

  void unregisterDevice(UUID deviceId);
}
