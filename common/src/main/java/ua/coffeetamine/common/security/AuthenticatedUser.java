package ua.coffeetamine.common.security;

import java.util.UUID;

/**
 * Typed view of the authenticated caller, materialized from the current JWT plus the OIDC identity
 * mapping. {@link #id} is the internal {@code app_user_id} (resolved via {@code
 * UserIdentityResolver} from the JWT {@code (iss, sub)} tuple), not the raw JWT subject — feature
 * modules must never see the external subject string.
 *
 * <p>Other fields are best-effort claim reads: any may be {@code null} if the IdP didn't supply the
 * corresponding claim.
 *
 * @param id internal app user UUID (NOT the JWT {@code sub}).
 * @param preferredUsername JWT {@code preferred_username} claim — display-friendly handle.
 * @param email JWT {@code email} claim (owner views only — never expose to other users).
 * @param givenName JWT {@code given_name} claim.
 * @param fullName JWT {@code name} claim.
 */
public record AuthenticatedUser(
    UUID id, String preferredUsername, String email, String givenName, String fullName) {}
