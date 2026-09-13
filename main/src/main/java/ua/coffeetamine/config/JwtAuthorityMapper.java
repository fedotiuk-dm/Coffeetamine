package ua.coffeetamine.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import ua.coffeetamine.common.security.Roles;

import lombok.RequiredArgsConstructor;

import org.jspecify.annotations.NonNull;

/**
 * Converts a validated {@link Jwt} into a {@link JwtAuthenticationToken} with the appropriate
 * Spring authorities. Every authenticated principal gets {@code ROLE_USER}; admins are determined
 * by the backend-owned {@link AdminAllowlistProperties#adminEmails()} list (case-insensitive match
 * on the JWT {@code email} claim — the allowlist is already lower-cased at binding time). IdP-
 * supplied role claims are intentionally ignored.
 *
 * <p>ADMIN elevation additionally requires the JWT {@code email_verified} claim to be {@code true}.
 * Without this guard a self-service-registered user could pick the admin email at sign-up and ride
 * the allowlist; trusting only verified addresses closes that loop because the IdP only stamps
 * {@code email_verified=true} after the standard email-confirmation flow. The {@code (issuer,
 * subject)} allowlist tracked separately is the stronger long-term form — see {@code
 * architecture/OIDC_BACKEND_REQUIREMENTS.md}.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthorityMapper implements Converter<Jwt, JwtAuthenticationToken> {

  private final AdminAllowlistProperties allowlist;

  @Override
  public JwtAuthenticationToken convert(@NonNull Jwt jwt) {
    List<GrantedAuthority> authorities = new ArrayList<>();
    authorities.add(new SimpleGrantedAuthority("ROLE_" + Roles.USER));

    if (isAdminCandidate(jwt)) {
      authorities.add(new SimpleGrantedAuthority("ROLE_" + Roles.ADMIN));
    }

    return new JwtAuthenticationToken(jwt, authorities, Objects.requireNonNull(jwt.getSubject()));
  }

  private boolean isAdminCandidate(Jwt jwt) {
    String email = jwt.getClaimAsString("email");
    if (email == null || !allowlist.adminEmails().contains(email.toLowerCase(Locale.ROOT))) {
      return false;
    }
    return Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));
  }
}
