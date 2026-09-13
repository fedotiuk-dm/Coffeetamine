package ua.coffeetamine.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import org.junit.jupiter.api.Test;

class JwtAuthorityMapperTest {

  private static Jwt jwt(String email, Boolean emailVerified) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", "x");
    if (email != null) {
      claims.put("email", email);
    }
    if (emailVerified != null) {
      claims.put("email_verified", emailVerified);
    }
    return new Jwt(
        "t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "RS256"), claims);
  }

  private static Set<String> authorities(AbstractAuthenticationToken token) {
    return token.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toSet());
  }

  @Test
  void grants_role_user_to_every_authenticated_principal() {
    JwtAuthorityMapper mapper = new JwtAuthorityMapper(new AdminAllowlistProperties(List.of()));

    var token = Objects.requireNonNull(mapper.convert(jwt("plain@example.com", true)));

    assertThat(authorities(token)).contains("ROLE_USER");
    assertThat(authorities(token)).doesNotContain("ROLE_ADMIN");
  }

  @Test
  void grants_role_admin_when_email_on_allowlist_case_insensitive_and_verified() {
    JwtAuthorityMapper mapper =
        new JwtAuthorityMapper(new AdminAllowlistProperties(List.of("Admin@Example.com")));

    var token = Objects.requireNonNull(mapper.convert(jwt("admin@EXAMPLE.com", true)));

    assertThat(authorities(token)).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
  }

  @Test
  void withholds_admin_when_email_matches_but_email_verified_is_false() {
    JwtAuthorityMapper mapper =
        new JwtAuthorityMapper(new AdminAllowlistProperties(List.of("admin@example.com")));

    var token = Objects.requireNonNull(mapper.convert(jwt("admin@example.com", false)));

    assertThat(authorities(token)).containsExactly("ROLE_USER");
  }

  @Test
  void withholds_admin_when_email_verified_claim_absent() {
    JwtAuthorityMapper mapper =
        new JwtAuthorityMapper(new AdminAllowlistProperties(List.of("admin@example.com")));

    var token = Objects.requireNonNull(mapper.convert(jwt("admin@example.com", null)));

    assertThat(authorities(token)).containsExactly("ROLE_USER");
  }

  @Test
  void survives_token_without_email_claim() {
    JwtAuthorityMapper mapper =
        new JwtAuthorityMapper(new AdminAllowlistProperties(List.of("admin@example.com")));

    var token = Objects.requireNonNull(mapper.convert(jwt(null, true)));

    assertThat(authorities(token)).containsExactly("ROLE_USER");
  }
}
