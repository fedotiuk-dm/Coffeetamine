package ua.coffeetamine.discovery.mapper;

import org.springframework.data.domain.Page;

import ua.coffeetamine.api.discovery.dto.DiscoveryUserDetailResponse;
import ua.coffeetamine.api.discovery.dto.GeoCoordinates;
import ua.coffeetamine.api.discovery.dto.Interest;
import ua.coffeetamine.api.discovery.dto.MoodBoardImage;
import ua.coffeetamine.api.discovery.dto.MoodBoardResponse;
import ua.coffeetamine.api.discovery.dto.NearbyUser;
import ua.coffeetamine.api.discovery.dto.NearbyUserListResponse;
import ua.coffeetamine.api.discovery.dto.PresenceResponse;
import ua.coffeetamine.api.discovery.dto.PublicUserProfileResponse;
import ua.coffeetamine.common.config.CentralMapperConfig;
import ua.coffeetamine.common.spi.InterestSummary;
import ua.coffeetamine.common.spi.MoodBoardImageSummary;
import ua.coffeetamine.common.spi.MoodBoardSummary;
import ua.coffeetamine.common.spi.NearbyPresence;
import ua.coffeetamine.common.spi.UserProfileSummary;
import ua.coffeetamine.discovery.readmodel.DiscoveryUserDetailView;
import ua.coffeetamine.discovery.readmodel.NearbyUserView;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface DiscoveryMapper {

  @Mapping(target = "userId", source = "profile.userId")
  @Mapping(target = "name", source = "profile.name")
  @Mapping(target = "avatarUrl", source = "profile.avatarUrl")
  @Mapping(target = "location", source = "presence")
  @Mapping(target = "mood", source = "presence.mood")
  NearbyUser toNearbyUser(NearbyUserView view);

  NearbyUserListResponse toNearbyUserListResponse(Page<NearbyUserView> page);

  DiscoveryUserDetailResponse toDetailResponse(DiscoveryUserDetailView view);

  PublicUserProfileResponse toPublicProfile(UserProfileSummary summary);

  @Mapping(target = "location", source = "presence")
  @Mapping(target = "mood", source = "mood")
  PresenceResponse toPresenceResponse(NearbyPresence presence);

  GeoCoordinates toGeoCoordinates(NearbyPresence presence);

  MoodBoardResponse toMoodBoardResponse(MoodBoardSummary summary);

  MoodBoardImage toMoodBoardImage(MoodBoardImageSummary summary);

  @Mapping(target = "isActive", constant = "true")
  Interest toInterest(InterestSummary summary);
}
