package ua.coffeetamine.interests.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import ua.coffeetamine.api.interests.dto.CreateInterestRequest;
import ua.coffeetamine.api.interests.dto.Interest;
import ua.coffeetamine.api.interests.dto.InterestListResponse;
import ua.coffeetamine.api.interests.dto.UpdateInterestRequest;
import ua.coffeetamine.api.interests.dto.UpdateUserInterestsRequest;
import ua.coffeetamine.api.interests.dto.UserInterestsResponse;

public interface InterestService {

  InterestListResponse listInterests(
      String search, String category, Boolean activeOnly, Pageable pageable);

  Interest getInterest(UUID id);

  Interest createInterest(CreateInterestRequest request);

  Interest updateInterest(UUID id, UpdateInterestRequest request);

  void deactivateInterest(UUID id);

  UserInterestsResponse getUserInterests(UUID userId);

  UserInterestsResponse getMyInterests();

  UserInterestsResponse replaceMyInterests(UpdateUserInterestsRequest request);

  /**
   * System-level replace by explicit user ID. Used by the cross-module {@code UserInterestsWriter}
   * during onboarding so the write joins the caller's @Transactional. Same semantics as {@link
   * #replaceMyInterests} minus the {@code SecurityUtils} lookup.
   */
  void replaceUserInterests(UUID userId, List<UUID> interestIds);
}
