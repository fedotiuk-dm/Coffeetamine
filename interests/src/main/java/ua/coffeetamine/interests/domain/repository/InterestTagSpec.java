package ua.coffeetamine.interests.domain.repository;

import org.springframework.data.jpa.domain.Specification;

import ua.coffeetamine.common.domain.repository.GenericSpecification;
import ua.coffeetamine.interests.domain.model.InterestTag;
import ua.coffeetamine.interests.domain.model.InterestTag_;

import lombok.experimental.UtilityClass;

@UtilityClass
public class InterestTagSpec {

  public static Specification<InterestTag> catalogFilter(
      String search, String category, Boolean activeOnly) {
    return GenericSpecification.<InterestTag>spec()
        .andIfNotBlank(
            search,
            s -> GenericSpecification.search(s, InterestTag_.CODE, InterestTag_.DISPLAY_NAME))
        .andIfNotBlank(category, c -> GenericSpecification.eq(InterestTag_.CATEGORY, c))
        .and(Boolean.TRUE.equals(activeOnly) ? GenericSpecification.isActive() : null)
        .build();
  }
}
