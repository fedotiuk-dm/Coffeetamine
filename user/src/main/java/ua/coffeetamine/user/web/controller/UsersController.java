package ua.coffeetamine.user.web.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import ua.coffeetamine.api.user.UsersApi;
import ua.coffeetamine.api.user.dto.OnboardingRequest;
import ua.coffeetamine.api.user.dto.PublicUserProfileResponse;
import ua.coffeetamine.api.user.dto.UpdateUserProfileRequest;
import ua.coffeetamine.api.user.dto.UserProfileResponse;
import ua.coffeetamine.common.security.RequiresAuthenticated;
import ua.coffeetamine.user.service.UserProfileService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class UsersController implements UsersApi {

  private final UserProfileService service;

  @Override
  @RequiresAuthenticated
  public ResponseEntity<UserProfileResponse> getMyUserProfile() {
    return ResponseEntity.ok(service.getMyProfile());
  }

  @Override
  @RequiresAuthenticated
  public ResponseEntity<UserProfileResponse> updateMyUserProfile(UpdateUserProfileRequest body) {
    return ResponseEntity.ok(service.updateMyProfile(body));
  }

  @Override
  @RequiresAuthenticated
  public ResponseEntity<UserProfileResponse> completeOnboarding(OnboardingRequest body) {
    return ResponseEntity.ok(service.completeOnboarding(body));
  }

  @Override
  @RequiresAuthenticated
  public ResponseEntity<PublicUserProfileResponse> getPublicUserProfile(UUID userId) {
    return ResponseEntity.ok(service.getPublicProfile(userId));
  }
}
