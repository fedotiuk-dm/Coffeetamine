package ua.coffeetamine.user.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.api.user.dto.OnboardingRequest;
import ua.coffeetamine.api.user.dto.PublicUserProfileResponse;
import ua.coffeetamine.api.user.dto.UpdateUserProfileRequest;
import ua.coffeetamine.api.user.dto.UserProfileResponse;
import ua.coffeetamine.common.exception.ErrorCode;
import ua.coffeetamine.common.exception.NotFoundException;
import ua.coffeetamine.common.security.AuthenticatedUser;
import ua.coffeetamine.common.security.SecurityUtils;
import ua.coffeetamine.common.spi.MoodBoardWriter;
import ua.coffeetamine.common.spi.UserInterestsWriter;
import ua.coffeetamine.user.domain.model.UserProfile;
import ua.coffeetamine.user.domain.repository.UserProfileRepository;
import ua.coffeetamine.user.mapper.UserProfileMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileServiceImpl implements UserProfileService {

  private final UserProfileRepository repository;
  private final UserProfileMapper mapper;
  private final UserInterestsWriter interestsWriter;
  private final MoodBoardWriter moodBoardWriter;
  private final SecurityUtils securityUtils;

  @Override
  public UserProfileResponse getMyProfile() {
    AuthenticatedUser caller = securityUtils.getCurrentUser();
    UserProfile profile = requireOnboardedProfile(caller.id());
    return mapper.toResponse(profile, caller.email());
  }

  @Override
  @Transactional
  public UserProfileResponse updateMyProfile(UpdateUserProfileRequest request) {
    AuthenticatedUser caller = securityUtils.getCurrentUser();
    UserProfile profile = requireOnboardedProfile(caller.id());
    mapper.updateFromRequest(request, profile);
    return mapper.toResponse(profile, caller.email());
  }

  @Override
  public PublicUserProfileResponse getPublicProfile(UUID userId) {
    return repository
        .findById(userId)
        .map(mapper::toPublicResponse)
        .orElseThrow(() -> new NotFoundException("UserProfile", userId));
  }

  /**
   * Atomic onboarding write: profile + interests + mood-board all share this transaction so any
   * failure rolls back every part. Re-running the wizard overwrites the same row (idempotent from
   * the user's perspective).
   */
  @Override
  @Transactional
  public UserProfileResponse completeOnboarding(OnboardingRequest request) {
    AuthenticatedUser caller = securityUtils.getCurrentUser();
    UserProfile profile =
        repository
            .findById(caller.id())
            .orElseGet(
                () -> repository.save(UserProfile.builder().userId(caller.id()).name("").build()));

    mapper.completeOnboarding(request, profile);
    interestsWriter.replaceSelections(caller.id(), request.getInterestIds());
    moodBoardWriter.replaceImages(
        caller.id(), mapper.toMoodBoardInputs(request.getMoodBoardImages()));

    return mapper.toResponse(profile, caller.email());
  }

  /**
   * Read-side guard: profile must exist AND have completed onboarding. Returns 404 with {@code
   * USER_ONBOARDING_INCOMPLETE} otherwise so the mobile client can transition to the wizard.
   */
  private UserProfile requireOnboardedProfile(UUID userId) {
    UserProfile profile =
        repository
            .findById(userId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        ErrorCode.USER_ONBOARDING_INCOMPLETE,
                        "User profile not initialised — complete the onboarding wizard first"));
    if (!profile.isOnboardingCompleted()) {
      throw new NotFoundException(
          ErrorCode.USER_ONBOARDING_INCOMPLETE, "Onboarding wizard has not been completed");
    }
    return profile;
  }
}
