package ua.coffeetamine.common.security;

import lombok.experimental.UtilityClass;

/**
 * Role constants used by the security layer. {@link RequiresAdmin}/{@link RequiresAuthenticated}
 * resolve to {@code ROLE_*} authorities granted by {@code JwtAuthorityMapper}. Coffeetamine MVP has
 * two roles only: {@link #USER} (every onboarded account) and {@link #ADMIN} (moderation /
 * back-office). Role names are mapped to Spring authorities as {@code ROLE_*} by the JWT converter
 * in {@code SecurityConfig}, so {@code @PreAuthorize("hasRole(...)")} works without further wiring.
 *
 * <p>Prefer the {@code @Requires*} meta annotations ({@link RequiresAdmin}, {@link
 * RequiresAuthenticated}) over raw {@code @PreAuthorize("hasRole('...')")} strings — they are
 * refactor-safe and greppable.
 */
@UtilityClass
public class Roles {

  public static final String USER = "USER";
  public static final String ADMIN = "ADMIN";
}
