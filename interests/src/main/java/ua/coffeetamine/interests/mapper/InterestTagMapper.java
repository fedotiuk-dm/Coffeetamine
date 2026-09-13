package ua.coffeetamine.interests.mapper;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;

import ua.coffeetamine.api.interests.dto.CreateInterestRequest;
import ua.coffeetamine.api.interests.dto.Interest;
import ua.coffeetamine.api.interests.dto.InterestListResponse;
import ua.coffeetamine.api.interests.dto.UpdateInterestRequest;
import ua.coffeetamine.api.interests.dto.UserInterestsResponse;
import ua.coffeetamine.common.config.CentralMapperConfig;
import ua.coffeetamine.interests.domain.model.InterestTag;
import ua.coffeetamine.interests.domain.model.UserInterestSelection;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(config = CentralMapperConfig.class)
public interface InterestTagMapper {

  @Mapping(target = "isActive", source = "active")
  Interest toResponse(InterestTag entity);

  @BeanMapping(
      ignoreByDefault = true,
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "code", source = "code")
  @Mapping(target = "displayName", source = "displayName")
  @Mapping(target = "category", source = "category")
  @Mapping(target = "sortOrder", source = "sortOrder")
  @Mapping(target = "active", constant = "true")
  InterestTag toEntity(CreateInterestRequest request);

  @BeanMapping(
      ignoreByDefault = true,
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "displayName")
  @Mapping(target = "category")
  @Mapping(target = "active", source = "isActive")
  @Mapping(target = "sortOrder")
  void updateFromRequest(UpdateInterestRequest request, @MappingTarget InterestTag entity);

  @BeanMapping(ignoreByDefault = true)
  @Mapping(target = "userId", source = "userId")
  @Mapping(target = "interest", source = "tag")
  @Mapping(target = "selectionOrder", source = "selectionOrder")
  UserInterestSelection toSelection(UUID userId, InterestTag tag, Integer selectionOrder);

  InterestListResponse toListResponse(Page<InterestTag> page);

  @Mapping(target = "interests", source = "interests")
  UserInterestsResponse toUserInterestsResponse(UUID userId, List<InterestTag> interests);
}
