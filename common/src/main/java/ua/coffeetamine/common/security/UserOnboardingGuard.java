package ua.coffeetamine.common.security;

import java.util.UUID;

/**
 * Cross-cutting policy: only callers who have finished the onboarding wizard may write user-state
 * (presence, interests, mood-board, pings). Lives in {@code common} because the user module itself
 * depends on interests + mood-board for atomic onboarding writes, so the impl cannot sit inside
 * user without creating a Maven cycle. The impl lives in {@code user/integration/} where it reads
 * the user-profile repository to evaluate the {@code onboardingCompleted} flag.
 *
 * <p>Throws {@code NotFoundException(USER_ONBOARDING_INCOMPLETE)} when the profile is absent or
 * still flagged incomplete — mobile clients dispatch on the code to bounce back into the wizard.
 *
 * <p>Callers pass the internal {@code app_user_id} explicitly — typically {@code
 * securityUtils.getCurrentUserId()} — so this contract has no implicit dependency on the security
 * context.
 */
public interface UserOnboardingGuard {

  /** Verifies the given user has completed onboarding. Throws otherwise. */
  void requireOnboardingComplete(UUID userId);
}
