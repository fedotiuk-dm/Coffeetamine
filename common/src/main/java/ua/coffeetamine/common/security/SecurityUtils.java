package ua.coffeetamine.common.security;

import java.util.Objects;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import ua.coffeetamine.common.identity.UserIdentityResolver;

import lombok.RequiredArgsConstructor;

/**
 * Imperative companion to the {@code @Requires*} annotations. JWT claim parsing lives here
 * exclusively; feature modules inject this bean and call {@link #getCurrentUser()} / {@link
 * #getCurrentUserId()} instead of touching the {@link SecurityContextHolder} directly.
 *
 * <p>{@code getCurrentUserId()} returns the internal {@code app_user_id} UUID resolved via {@link
 * UserIdentityResolver} from the JWT {@code (iss, sub)} tuple. Feature modules never see the raw
 * subject string — that keeps the backend decoupled from the OIDC provider's subject format.
 */
@Component
@RequiredArgsConstructor
public class SecurityUtils {

  private final UserIdentityResolver resolver;

  public AuthenticatedUser getCurrentUser() {
    Jwt jwt = requireJwt();
    UUID internalId = resolveInternalId(jwt);
    return new AuthenticatedUser(
        internalId,
        jwt.getClaimAsString("preferred_username"),
        jwt.getClaimAsString("email"),
        jwt.getClaimAsString("given_name"),
        jwt.getClaimAsString("name"));
  }

  /** Internal app user UUID resolved from JWT {@code (iss, sub)}. */
  public UUID getCurrentUserId() {
    return resolveInternalId(requireJwt());
  }

  /** Plain role name (no {@code ROLE_} prefix). Use {@link Roles} constants. */
  public boolean hasRole(String role) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      return false;
    }
    String authority = "ROLE_" + role;
    return auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
  }

  public boolean isAdmin() {
    return hasRole(Roles.ADMIN);
  }

  public void verifyOwnerOrAdmin(UUID resourceOwnerId) {
    if (isAdmin()) {
      return;
    }
    if (!getCurrentUserId().equals(resourceOwnerId)) {
      throw new AccessDeniedException("Operation not permitted for current principal");
    }
  }

  private UUID resolveInternalId(Jwt jwt) {
    return resolver.resolve(
        Objects.requireNonNull(jwt.getIssuer()).toString(),
        jwt.getSubject(),
        jwt.getClaimAsString("email"));
  }

  private static Jwt requireJwt() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jwtAuth) {
      return jwtAuth.getToken();
    }
    throw new IllegalStateException("No authenticated JWT principal in security context");
  }
}
