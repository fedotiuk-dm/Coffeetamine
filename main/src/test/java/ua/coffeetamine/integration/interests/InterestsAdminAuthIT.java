package ua.coffeetamine.integration.interests;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import ua.coffeetamine.integration.BaseIntegrationTest;
import ua.coffeetamine.integration.TestAuthSupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Locks down the role split on {@code /api/admin/interests}: any USER is rejected with 403, and the
 * 403 response carries the shared RFC 7807 shape ({@code errorCode=AUTH_003}). ADMIN passes
 * through.
 */
class InterestsAdminAuthIT extends BaseIntegrationTest {

  @Autowired JsonMapper jsonMapper;
  @Autowired TestAuthSupport authSupport;

  @Test
  @DisplayName("POST /api/admin/interests by USER → 403 AUTH_003 problem+json")
  void plainUserForbiddenFromAdminEndpoint() throws Exception {
    UUID user = testData.createOnboardedUser("Plain");
    String payload =
        jsonMapper.writeValueAsString(
            java.util.Map.of(
                "code", "FRESH",
                "displayName", "Fresh",
                "category", "DRINKS"));

    mockMvc
        .perform(
            post("/api/admin/interests")
                .with(authSupport.userJwt(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("AUTH_003"))
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  @DisplayName("POST /api/admin/interests by ADMIN → 201 with body")
  void adminCanCreateTag() throws Exception {
    UUID admin = testData.createOnboardedUser("Admin");
    String payload =
        jsonMapper.writeValueAsString(
            java.util.Map.of(
                "code", "DECAF",
                "displayName", "Decaf",
                "category", "DRINKS"));

    mockMvc
        .perform(
            post("/api/admin/interests")
                .with(authSupport.adminJwt(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("DECAF"))
        .andExpect(jsonPath("$.displayName").value("Decaf"));
  }
}
