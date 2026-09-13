package ua.coffeetamine.discovery.service;

import java.util.UUID;

import org.springframework.data.domain.Pageable;

import ua.coffeetamine.api.discovery.dto.DiscoveryUserDetailResponse;
import ua.coffeetamine.api.discovery.dto.NearbyUserListResponse;

public interface DiscoveryService {

  NearbyUserListResponse listNearbyUsers(
      Integer radiusMeters, Integer minCompatibility, Pageable pageable);

  DiscoveryUserDetailResponse getDiscoveryUserDetail(UUID userId);
}
