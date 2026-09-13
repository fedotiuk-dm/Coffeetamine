package ua.coffeetamine.user.service;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.common.exception.ErrorCode;
import ua.coffeetamine.common.exception.NotFoundException;
import ua.coffeetamine.common.security.UserOnboardingGuard;
import ua.coffeetamine.user.domain.model.UserProfile;
import ua.coffeetamine.user.domain.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * The user module supplies the implementation of the {@code common} {@link UserOnboardingGuard}
 * SPI. Other modules depend only on the {@code common} interface (injected by Spring) — never on
 * this class — so they stay decoupled from {@code user}.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class UserOnboardingGuardImpl implements UserOnboardingGuard {

  private final UserProfileRepository repository;

  @Override
  public void requireOnboardingComplete(UUID userId) {
    boolean onboarded =
        repository.findById(userId).map(UserProfile::isOnboardingCompleted).orElse(false);
    if (!onboarded) {
      throw new NotFoundException(
          ErrorCode.USER_ONBOARDING_INCOMPLETE,
          "Onboarding wizard has not been completed for user " + userId);
    }
  }
}
