package ua.coffeetamine.notification.web.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import ua.coffeetamine.api.notification.NotificationDevicesApi;
import ua.coffeetamine.api.notification.dto.Device;
import ua.coffeetamine.api.notification.dto.DeviceListResponse;
import ua.coffeetamine.api.notification.dto.RegisterDeviceRequest;
import ua.coffeetamine.common.security.RequiresAuthenticated;
import ua.coffeetamine.notification.service.DeviceService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequiresAuthenticated
public class DevicesController implements NotificationDevicesApi {

  private final DeviceService service;

  @Override
  public ResponseEntity<DeviceListResponse> listMyDevices(Pageable pageable) {
    return ResponseEntity.ok(service.listMyDevices(pageable));
  }

  @Override
  public ResponseEntity<Device> registerDevice(RegisterDeviceRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.registerDevice(request));
  }

  @Override
  public ResponseEntity<Void> unregisterDevice(UUID deviceId) {
    service.unregisterDevice(deviceId);
    return ResponseEntity.noContent().build();
  }
}
