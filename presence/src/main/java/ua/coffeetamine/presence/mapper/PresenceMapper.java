package ua.coffeetamine.presence.mapper;

import ua.coffeetamine.api.presence.dto.PresenceResponse;
import ua.coffeetamine.api.presence.dto.UpdatePresenceRequest;
import ua.coffeetamine.common.config.CentralMapperConfig;
import ua.coffeetamine.presence.domain.model.UserPresence;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralMapperConfig.class)
public interface PresenceMapper {

  PresenceResponse toResponse(UserPresence entity);

  /**
   * PUT-style copy: {@code status} is required by the OpenAPI schema (Bean Validation rejects
   * absent), and {@code mood} is either a non-blank string (validated by {@code minLength: 1}) or
   * absent → {@code null}, which clears the field. No magic sentinel, no null-skipping strategy.
   * Location is handled in the service because it requires server-side jitter.
   */
  @BeanMapping(ignoreByDefault = true)
  @Mapping(target = "status")
  @Mapping(target = "mood")
  void updateFromRequest(UpdatePresenceRequest request, @MappingTarget UserPresence entity);

  @BeanMapping(ignoreByDefault = true)
  @Mapping(target = "status")
  @Mapping(target = "mood")
  @Mapping(target = "location")
  void clear(ClearPresenceCommand command, @MappingTarget UserPresence entity);
}
