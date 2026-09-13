package ua.coffeetamine.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import ua.coffeetamine.common.identity.UserIdentityResolver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SecurityUtilsTest {

  @Mock private UserIdentityResolver resolver;

  @InjectMocks private SecurityUtils securityUtils;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private static Jwt jwt(String subject) {
    return new Jwt(
        "token",
        Instant.now(),
        Instant.now().plusSeconds(3600),
        Map.of("alg", "RS256"),
        Map.of(
            "sub",
            subject,
            "iss",
            "https://auth.example.com",
            "email",
            "u@example.com",
            "preferred_username",
            "dev",
            "given_name",
            "Dev",
            "name",
            "Dev User"));
  }

  private static void putContext(Jwt token) {
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(token, List.of()));
  }

  @Test
  void getCurrentUserId_returns_resolved_internal_uuid_for_opaque_subject() {
    UUID internal = UUID.randomUUID();
    when(resolver.resolve(
            eq("https://auth.example.com"), eq("opaque-zitadel-sub-1234"), anyString()))
        .thenReturn(internal);
    putContext(jwt("opaque-zitadel-sub-1234"));

    UUID result = securityUtils.getCurrentUserId();

    assertThat(result).isEqualTo(internal);
  }

  @Test
  void getCurrentUser_carries_internal_uuid_not_jwt_subject() {
    UUID internal = UUID.randomUUID();
    when(resolver.resolve(anyString(), anyString(), anyString())).thenReturn(internal);
    putContext(jwt("not-a-uuid"));

    AuthenticatedUser user = securityUtils.getCurrentUser();

    assertThat(user.id()).isEqualTo(internal);
    assertThat(user.email()).isEqualTo("u@example.com");
    assertThat(user.preferredUsername()).isEqualTo("dev");
  }

  @Test
  void throws_when_no_jwt_principal() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("x", "y", List.of()));

    assertThatThrownBy(securityUtils::getCurrentUserId).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void hasRole_unaffected_by_resolver() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "x", "y", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

    assertThat(securityUtils.isAdmin()).isTrue();
  }
}
