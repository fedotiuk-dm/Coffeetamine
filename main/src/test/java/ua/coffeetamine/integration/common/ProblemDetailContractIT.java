package ua.coffeetamine.integration.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import ua.coffeetamine.integration.BaseIntegrationTest;
import ua.coffeetamine.integration.TestAuthSupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Locks down the shared RFC 7807 error contract. Every error response — whether produced by the
 * security filter chain ({@link ua.coffeetamine.common.web.ProblemDetailSecurityHandler}) or by the
 * controller advice ({@link ua.coffeetamine.common.exception.GlobalExceptionHandler}) — must carry:
 *
 * <ul>
 *   <li>{@code Content-Type: application/problem+json}
 *   <li>numeric {@code status} matching the HTTP status
 *   <li>stable {@code errorCode} for client-side dispatch
 *   <li>{@code instance} pointing at the request path
 * </ul>
 *
 * Mobile dispatches on {@code errorCode} — drift in this shape breaks every error UI flow.
 */
class ProblemDetailContractIT extends BaseIntegrationTest {

  @Autowired private TestAuthSupport authSupport;

  @Test
  @DisplayName("no JWT → 401 AUTH_001 with full problem+json envelope")
  void unauthenticatedRequestProducesProblemDetail() throws Exception {
    mockMvc
        .perform(get("/api/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.errorCode").value("AUTH_001"))
        .andExpect(jsonPath("$.title").exists())
        .andExpect(jsonPath("$.instance").value("/api/users/me"));
  }

  @Test
  @DisplayName("404 from domain layer → problem+json with NF_002 (entity-not-found)")
  void domainNotFoundProducesProblemDetail() throws Exception {
    UUID viewer = testData.createOnboardedUser("Viewer");

    // UserProfileServiceImpl.getPublicProfile throws `new NotFoundException("UserProfile", id)`
    // — the (entityType, id) ctor uses the generic NF_002 entity-not-found code, not USER_*.
    mockMvc
        .perform(get("/api/users/{userId}", UUID.randomUUID()).with(authSupport.userJwt(viewer)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.errorCode").value("NF_002"))
        .andExpect(jsonPath("$.instance").exists());
  }

  @Test
  @DisplayName("ProblemDetail has no leaked stack-trace fields")
  void problemDetailDoesNotLeakInternals() throws Exception {
    mockMvc
        .perform(get("/api/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.stackTrace").doesNotExist())
        .andExpect(jsonPath("$.cause").doesNotExist())
        .andExpect(jsonPath("$.exception").doesNotExist())
        .andExpect(header().doesNotExist("X-Exception-Type"));
  }
}
