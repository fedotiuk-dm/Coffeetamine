package ua.coffeetamine.user.service;

import java.util.UUID;

import ua.coffeetamine.api.user.dto.OnboardingRequest;
import ua.coffeetamine.api.user.dto.PublicUserProfileResponse;
import ua.coffeetamine.api.user.dto.UpdateUserProfileRequest;
import ua.coffeetamine.api.user.dto.UserProfileResponse;

public interface UserProfileService {

  /** Owner view for the authenticated user. Auto-creates the row on first call. */
  UserProfileResponse getMyProfile();

  /** Update basic info on the authenticated user's profile. */
  UserProfileResponse updateMyProfile(UpdateUserProfileRequest request);

  /** Public view for another user. */
  PublicUserProfileResponse getPublicProfile(UUID userId);

  /**
   * Complete the onboarding wizard. Applies basic-info fields, flips {@code onboardingCompleted} to
   * true. Interest + mood-board payload fields are accepted but not yet applied here — that
   * coordination will move into this service once the interests and mood-board integration ports
   * land.
   */
  UserProfileResponse completeOnboarding(OnboardingRequest request);
}
