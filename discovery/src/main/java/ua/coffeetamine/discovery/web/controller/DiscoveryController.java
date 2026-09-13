package ua.coffeetamine.discovery.web.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import ua.coffeetamine.api.discovery.DiscoveryApi;
import ua.coffeetamine.api.discovery.dto.DiscoveryUserDetailResponse;
import ua.coffeetamine.api.discovery.dto.NearbyUserListResponse;
import ua.coffeetamine.common.security.RequiresAuthenticated;
import ua.coffeetamine.discovery.service.DiscoveryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequiresAuthenticated
public class DiscoveryController implements DiscoveryApi {

  private final DiscoveryService service;

  @Override
  public ResponseEntity<NearbyUserListResponse> listNearbyUsers(
      Integer radiusMeters, Integer minCompatibility, Pageable pageable) {
    return ResponseEntity.ok(service.listNearbyUsers(radiusMeters, minCompatibility, pageable));
  }

  @Override
  public ResponseEntity<DiscoveryUserDetailResponse> getDiscoveryUserDetail(UUID userId) {
    return ResponseEntity.ok(service.getDiscoveryUserDetail(userId));
  }
}
