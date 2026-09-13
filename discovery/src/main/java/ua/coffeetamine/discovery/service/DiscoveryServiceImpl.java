package ua.coffeetamine.discovery.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.api.discovery.dto.DiscoveryUserDetailResponse;
import ua.coffeetamine.api.discovery.dto.NearbyUserListResponse;
import ua.coffeetamine.common.domain.model.GeoConstants;
import ua.coffeetamine.common.exception.ConflictException;
import ua.coffeetamine.common.exception.ErrorCode;
import ua.coffeetamine.common.exception.NotFoundException;
import ua.coffeetamine.common.security.SecurityUtils;
import ua.coffeetamine.common.spi.InterestSummary;
import ua.coffeetamine.common.spi.MoodBoardLookup;
import ua.coffeetamine.common.spi.MoodBoardSummary;
import ua.coffeetamine.common.spi.NearbyPresence;
import ua.coffeetamine.common.spi.PresenceLookup;
import ua.coffeetamine.common.spi.UserInterestsLookup;
import ua.coffeetamine.common.spi.UserProfileLookup;
import ua.coffeetamine.common.spi.UserProfileSummary;
import ua.coffeetamine.discovery.config.DiscoveryProperties;
import ua.coffeetamine.discovery.mapper.DiscoveryMapper;
import ua.coffeetamine.discovery.readmodel.DiscoveryUserDetailView;
import ua.coffeetamine.discovery.readmodel.NearbyUserView;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiscoveryServiceImpl implements DiscoveryService {

  private final PresenceLookup presenceLookup;
  private final UserProfileLookup userProfileLookup;
  private final UserInterestsLookup userInterestsLookup;
  private final MoodBoardLookup moodBoardLookup;
  private final DiscoveryMapper mapper;
  private final DiscoveryProperties properties;
  private final SecurityUtils securityUtils;

  @Override
  public NearbyUserListResponse listNearbyUsers(
      Integer radiusMeters, Integer minCompatibility, Pageable pageable) {
    int effectiveRadius = clampRadius(radiusMeters);
    int effectiveThreshold = clampThreshold(minCompatibility);
    UUID callerId = securityUtils.getCurrentUserId();
    NearbyPresence caller = requireDiscoverableCaller(callerId);
    Set<UUID> callerInterests = userInterestsLookup.interestIdsFor(callerId);

    List<NearbyPresence> candidates = candidatesInRadius(caller, effectiveRadius, callerId);
    List<UUID> candidateIds = candidates.stream().map(NearbyPresence::userId).toList();
    Map<UUID, UserProfileSummary> profiles = userProfileLookup.findAllByIds(candidateIds);
    Map<UUID, Set<UUID>> interests = userInterestsLookup.interestIdsFor(candidateIds);

    // SQL bounds query returns a square; the radius is a circle. Compatibility is computed in Java
    // from interest-tag overlap, so it can't be pushed to SQL either.
    List<NearbyUserView> views =
        candidates.stream()
            .map(c -> buildView(c, profiles.get(c.userId()), caller, callerInterests, interests))
            .filter(v -> v.profile() != null)
            .filter(v -> v.distanceMeters() <= effectiveRadius)
            .filter(v -> v.compatibilityScore() >= effectiveThreshold)
            .sorted(
                Comparator.comparing(NearbyUserView::compatibilityScore)
                    .reversed()
                    .thenComparing(NearbyUserView::distanceMeters))
            .toList();

    return mapper.toNearbyUserListResponse(slice(views, pageable));
  }

  private int clampRadius(Integer requested) {
    int value = requested == null ? properties.defaultRadiusMeters() : requested;
    return Math.clamp(value, 1, properties.maxRadiusMeters());
  }

  private int clampThreshold(Integer requested) {
    int value = requested == null ? properties.defaultMinCompatibility() : requested;
    return Math.clamp(value, properties.minAllowedCompatibility(), 100);
  }

  @Override
  public DiscoveryUserDetailResponse getDiscoveryUserDetail(UUID userId) {
    UUID callerId = securityUtils.getCurrentUserId();
    UserProfileSummary profile =
        userProfileLookup
            .findById(userId)
            .orElseThrow(() -> new NotFoundException("UserProfile", userId));
    NearbyPresence presence =
        presenceLookup
            .findReadyVisible(userId)
            .orElseThrow(() -> new NotFoundException("UserPresence", userId));
    MoodBoardSummary moodBoard =
        moodBoardLookup
            .findFor(userId)
            .orElseThrow(() -> new NotFoundException("MoodBoard", userId));
    List<InterestSummary> interests = userInterestsLookup.orderedInterestsFor(userId);

    Set<UUID> callerInterestIds = userInterestsLookup.interestIdsFor(callerId);
    Set<UUID> targetInterestIds =
        interests.stream().map(InterestSummary::id).collect(Collectors.toUnmodifiableSet());

    return mapper.toDetailResponse(
        new DiscoveryUserDetailView(
            profile,
            interests,
            moodBoard,
            presence,
            compatibilityScore(callerInterestIds, targetInterestIds),
            distanceFromCaller(callerId, presence)));
  }

  private NearbyPresence requireDiscoverableCaller(UUID callerId) {
    return presenceLookup
        .findReadyVisible(callerId)
        .orElseThrow(
            () ->
                new ConflictException(
                    ErrorCode.PRESENCE_NOT_READY,
                    "Current user must be Ready (with location) before discovery"));
  }

  private List<NearbyPresence> candidatesInRadius(
      NearbyPresence caller, int radiusMeters, UUID callerId) {
    double latitudeDelta = radiusMeters / GeoConstants.METERS_PER_LATITUDE_DEGREE;
    double longitudeDelta = radiusMeters / GeoConstants.metersPerLongitudeDegree(caller.latitude());
    return presenceLookup.findReadyCandidatesInBounds(
        callerId,
        caller.latitude() - latitudeDelta,
        caller.latitude() + latitudeDelta,
        caller.longitude() - longitudeDelta,
        caller.longitude() + longitudeDelta);
  }

  private static NearbyUserView buildView(
      NearbyPresence presence,
      UserProfileSummary profile,
      NearbyPresence caller,
      Set<UUID> callerInterests,
      Map<UUID, Set<UUID>> interestsByUser) {
    return new NearbyUserView(
        profile,
        presence,
        compatibilityScore(
            callerInterests, interestsByUser.getOrDefault(presence.userId(), Set.of())),
        distanceMeters(caller, presence));
  }

  private Integer distanceFromCaller(UUID callerId, NearbyPresence target) {
    return presenceLookup
        .findReadyVisible(callerId)
        .map(caller -> distanceMeters(caller, target))
        .orElse(null);
  }

  private static Integer distanceMeters(NearbyPresence from, NearbyPresence to) {
    double fromLat = Math.toRadians(from.latitude());
    double toLat = Math.toRadians(to.latitude());
    double latDelta = Math.toRadians(to.latitude() - from.latitude());
    double lngDelta = Math.toRadians(to.longitude() - from.longitude());
    double haversine =
        Math.pow(Math.sin(latDelta / 2.0), 2.0)
            + Math.cos(fromLat) * Math.cos(toLat) * Math.pow(Math.sin(lngDelta / 2.0), 2.0);
    double angularDistance = 2.0 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1.0 - haversine));
    return (int) Math.round(GeoConstants.EARTH_RADIUS_METERS * angularDistance);
  }

  private static Integer compatibilityScore(
      Set<UUID> callerInterests, Set<UUID> candidateInterests) {
    if (callerInterests.isEmpty() || candidateInterests.isEmpty()) {
      return 1;
    }
    long overlap = callerInterests.stream().filter(candidateInterests::contains).count();
    int denominator = Math.max(callerInterests.size(), candidateInterests.size());
    return Math.max(1, (int) Math.round((overlap * 100.0) / denominator));
  }

  private static Page<NearbyUserView> slice(List<NearbyUserView> views, Pageable pageable) {
    int fromIndex = (int) Math.min(pageable.getOffset(), views.size());
    int toIndex = Math.min(fromIndex + pageable.getPageSize(), views.size());
    return new PageImpl<>(views.subList(fromIndex, toIndex), pageable, views.size());
  }
}
