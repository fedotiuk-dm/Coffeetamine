package ua.coffeetamine.presence.service;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.api.presence.dto.PresenceResponse;
import ua.coffeetamine.api.presence.dto.UpdatePresenceRequest;
import ua.coffeetamine.common.domain.model.GeoConstants;
import ua.coffeetamine.common.exception.BadRequestException;
import ua.coffeetamine.common.exception.ErrorCode;
import ua.coffeetamine.common.security.SecurityUtils;
import ua.coffeetamine.common.security.UserOnboardingGuard;
import ua.coffeetamine.presence.config.PresenceProperties;
import ua.coffeetamine.presence.domain.model.JitteredLocation;
import ua.coffeetamine.presence.domain.model.PresenceState;
import ua.coffeetamine.presence.domain.model.UserPresence;
import ua.coffeetamine.presence.domain.repository.UserPresenceRepository;
import ua.coffeetamine.presence.mapper.ClearPresenceCommand;
import ua.coffeetamine.presence.mapper.PresenceMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PresenceServiceImpl implements PresenceService {

  private final UserPresenceRepository repository;
  private final PresenceMapper mapper;
  private final PresenceProperties properties;
  private final UserOnboardingGuard onboardingGuard;
  private final SecurityUtils securityUtils;

  @Override
  @Transactional
  public PresenceResponse getMyPresence() {
    return mapper.toResponse(repository.findOrCreate(securityUtils.getCurrentUserId()));
  }

  @Override
  @Transactional
  public PresenceResponse updateMyPresence(UpdatePresenceRequest request) {
    UUID callerId = securityUtils.getCurrentUserId();
    onboardingGuard.requireOnboardingComplete(callerId);
    UserPresence presence = repository.findOrCreate(callerId);
    mapper.updateFromRequest(request, presence);
    applyJitteredLocation(request, presence);
    requireReadyLocation(presence);
    return mapper.toResponse(presence);
  }

  @Override
  @Transactional
  public void clearMyPresence() {
    mapper.clear(
        ClearPresenceCommand.notReady(), repository.findOrCreate(securityUtils.getCurrentUserId()));
  }

  private void applyJitteredLocation(UpdatePresenceRequest request, UserPresence presence) {
    Double rawLatitude = request.getRawLatitude();
    Double rawLongitude = request.getRawLongitude();
    if ((rawLatitude == null) != (rawLongitude == null)) {
      throw new BadRequestException(
          ErrorCode.PRESENCE_LOCATION_MISSING,
          "Both rawLatitude and rawLongitude are required when updating location");
    }
    if (rawLatitude == null) {
      return;
    }
    presence.setLocation(jitter(rawLatitude, rawLongitude));
  }

  private static void requireReadyLocation(UserPresence presence) {
    if (presence.getStatus() == PresenceState.READY && presence.getLocation() == null) {
      throw new BadRequestException(
          ErrorCode.PRESENCE_LOCATION_MISSING, "Location is required when status is READY");
    }
  }

  private JitteredLocation jitter(double latitude, double longitude) {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    double distanceMeters = random.nextDouble(properties.jitterMaxMeters());
    double angle = random.nextDouble(Math.PI * 2.0);
    double northMeters = Math.cos(angle) * distanceMeters;
    double eastMeters = Math.sin(angle) * distanceMeters;

    double jitteredLatitude = latitude + northMeters / GeoConstants.METERS_PER_LATITUDE_DEGREE;
    double jitteredLongitude =
        longitude + eastMeters / GeoConstants.metersPerLongitudeDegree(latitude);
    return new JitteredLocation(
        Math.clamp(jitteredLatitude, -90.0, 90.0), wrapLongitude(jitteredLongitude));
  }

  private static double wrapLongitude(double longitude) {
    if (longitude > 180.0) {
      return longitude - 360.0;
    }
    if (longitude < -180.0) {
      return longitude + 360.0;
    }
    return longitude;
  }
}
