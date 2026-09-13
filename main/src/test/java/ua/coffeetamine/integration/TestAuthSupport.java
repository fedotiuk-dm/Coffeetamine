package ua.coffeetamine.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import ua.coffeetamine.common.identity.ExternalIdentity;
import ua.coffeetamine.common.identity.ExternalIdentityRepository;

/**
 * JWT helpers for MockMvc. The JWT subject is an opaque per-test string (mirroring real OIDC
 * providers like ZITADEL that don't emit UUID subjects); the test fixture seeds an {@link
 * ExternalIdentity} so {@code SecurityUtils.getCurrentUserId()} resolves to the desired internal
 * {@code app_user_id} UUID.
 *
 * <p>Roles are placed directly as authorities — production code path goes through {@code
 * JwtAuthorityMapper}, but tests bypass it by hand-rolling the {@code JwtAuthenticationToken} via
 * spring-security-test's {@code jwt()} processor.
 */
@Component
public class TestAuthSupport {

  public static final String TEST_ISSUER = "http://localhost/test";

  @Autowired private ExternalIdentityRepository identityRepository;

  /** Returns a post-processor whose JWT resolves to the given internal {@code appUserId}. */
  public RequestPostProcessor userJwt(UUID appUserId) {
    String subject = seed(appUserId);
    return jwt()
        .jwt(j -> j.subject(subject).issuer(TEST_ISSUER).claim("email", appUserId + "@test"))
        .authorities(new SimpleGrantedAuthority("ROLE_USER"));
  }

  public RequestPostProcessor adminJwt(UUID appUserId) {
    String subject = seed(appUserId);
    return jwt()
        .jwt(j -> j.subject(subject).issuer(TEST_ISSUER).claim("email", appUserId + "@test"))
        .authorities(
            new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  private String seed(UUID appUserId) {
    String subject = "test-sub-" + appUserId;
    if (identityRepository.findByIssuerAndSubject(TEST_ISSUER, subject).isEmpty()) {
      identityRepository.save(
          ExternalIdentity.builder()
              .appUserId(appUserId)
              .issuer(TEST_ISSUER)
              .subject(subject)
              .build());
    }
    return subject;
  }
}
