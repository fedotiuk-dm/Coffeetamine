package ua.coffeetamine.user.mapper;

import java.util.List;

import ua.coffeetamine.api.user.dto.OnboardingRequest;
import ua.coffeetamine.api.user.dto.PublicUserProfileResponse;
import ua.coffeetamine.api.user.dto.UpdateUserProfileRequest;
import ua.coffeetamine.api.user.dto.UserProfileResponse;
import ua.coffeetamine.common.config.CentralMapperConfig;
import ua.coffeetamine.user.domain.model.UserProfile;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(config = CentralMapperConfig.class)
public interface UserProfileMapper {

  @Mapping(target = "email", source = "email")
  UserProfileResponse toResponse(UserProfile entity, String email);

  PublicUserProfileResponse toPublicResponse(UserProfile entity);

  @BeanMapping(
      ignoreByDefault = true,
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "name")
  @Mapping(target = "avatarUrl")
  @Mapping(target = "about")
  void updateFromRequest(UpdateUserProfileRequest request, @MappingTarget UserProfile entity);

  @BeanMapping(ignoreByDefault = true)
  @Mapping(target = "name")
  @Mapping(target = "avatarUrl")
  @Mapping(target = "about")
  @Mapping(target = "onboardingCompleted", constant = "true")
  void completeOnboarding(OnboardingRequest request, @MappingTarget UserProfile entity);

  List<ua.coffeetamine.common.spi.MoodBoardImageInput> toMoodBoardInputs(
      List<ua.coffeetamine.api.user.dto.MoodBoardImageInput> images);
}
