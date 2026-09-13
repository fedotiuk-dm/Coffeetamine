package ua.coffeetamine.integration.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import ua.coffeetamine.integration.BaseIntegrationTest;
import ua.coffeetamine.integration.TestAuthSupport;

/**
 * OpenAPI ↔ controller drift detection. Loads every {@code openapi/&lt;module&gt;-paths.yaml}
 * spec from the project-root {@code openapi/} folder, hits {@code GET /v3/api-docs/0-all} via
 * MockMvc to inspect the live controllers, and asserts the two views agree.
 *
 * <p>Catches three kinds of drift:
 *
 * <ul>
 *   <li><strong>Stale</strong> — path declared in a YAML but no controller implements it. Mobile
 *       would call a 404. Usually someone deleted an endpoint and forgot to update the spec.
 *   <li><strong>Undocumented</strong> — controller exists but no YAML mentions the path. Mobile
 *       has no idea the endpoint is there; it also bypasses the schema-driven request validation
 *       the mobile-generator relies on.
 *   <li><strong>Method mismatches</strong> — both sides have the path but disagree on HTTP
 *       methods (YAML has GET, controller added PUT without anyone updating the spec).
 * </ul>
 *
 * <p>Compared as a single aggregate so paths shared by multiple modules (e.g. the
 * {@code /api/users/&#123;userId&#125;/...} family belongs to several feature modules) cannot be
 * miscounted as "undocumented" just because the test couldn't decide which module they belong
 * to. The error report names the offending paths — finding which YAML to edit is a single
 * {@code grep}.
 *
 * <p>Framework-served paths under {@code /v3/api-docs}, {@code /swagger-ui}, actuator, and
 * Spring's {@code /error} are ignored — they're not part of the app contract.
 */
class OpenApiDriftIT extends BaseIntegrationTest {

  /**
   * Path to the project-root {@code openapi/} folder. Failsafe / Surefire run from each module's
   * basedir; from {@code main/} this resolves to the sibling {@code openapi/}.
   */
  private static final Path OPENAPI_DIR = Paths.get("..", "openapi");

  /** Every {@code *-paths.yaml} we expect to find in {@code openapi/}. */
  private static final List<String> YAML_FILES =
      List.of(
          "user-paths.yaml",
          "interests-paths.yaml",
          "presence-paths.yaml",
          "mood-board-paths.yaml",
          "discovery-paths.yaml",
          "ping-paths.yaml",
          "notification-paths.yaml");

  /** Framework-served paths the application doesn't own. */
  private static final List<String> IGNORED_PATH_PREFIXES =
      List.of("/v3/api-docs", "/swagger-ui", "/actuator", "/error");

  private static final Set<String> ALLOWED_HTTP_METHODS =
      Set.of("get", "post", "put", "patch", "delete", "head", "options");

  /** Synthetic JWT subject — needed to authorise the {@code /v3/api-docs/0-all} request. */
  private static final UUID DOC_USER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Autowired private JsonMapper jsonMapper;
  @Autowired private TestAuthSupport authSupport;

  @Test
  @DisplayName("every OpenAPI path is implemented and every controller path is documented")
  void specsAndControllersMustNotDrift() throws Exception {
    Map<String, Set<String>> yamlEndpoints = collectYamlEndpoints();
    Map<String, Set<String>> codeEndpoints = fetchControllerEndpoints();

    Set<String> stalePaths = new TreeSet<>(yamlEndpoints.keySet());
    stalePaths.removeAll(codeEndpoints.keySet());

    Set<String> undocumentedPaths = new TreeSet<>(codeEndpoints.keySet());
    undocumentedPaths.removeAll(yamlEndpoints.keySet());

    Set<String> methodMismatches = new TreeSet<>();
    for (Map.Entry<String, Set<String>> entry : yamlEndpoints.entrySet()) {
      Set<String> code = codeEndpoints.get(entry.getKey());
      if (code == null || code.equals(entry.getValue())) {
        continue;
      }
      Set<String> missingInYaml = new TreeSet<>(code);
      missingInYaml.removeAll(entry.getValue());
      Set<String> staleInYaml = new TreeSet<>(entry.getValue());
      staleInYaml.removeAll(code);
      StringBuilder sb = new StringBuilder(entry.getKey());
      if (!missingInYaml.isEmpty()) {
        sb.append(" — controller methods missing from YAML: ").append(missingInYaml);
      }
      if (!staleInYaml.isEmpty()) {
        sb.append(" — YAML methods missing from controller: ").append(staleInYaml);
      }
      methodMismatches.add(sb.toString());
    }

    StringBuilder report = new StringBuilder();
    if (!stalePaths.isEmpty()) {
      report.append("\n  STALE paths (in YAML, NOT in controllers):\n");
      stalePaths.forEach(p -> report.append("    - ").append(p).append("\n"));
    }
    if (!undocumentedPaths.isEmpty()) {
      report.append("\n  UNDOCUMENTED paths (in controllers, NOT in YAML):\n");
      undocumentedPaths.forEach(p -> report.append("    - ").append(p).append("\n"));
    }
    if (!methodMismatches.isEmpty()) {
      report.append("\n  METHOD mismatches:\n");
      methodMismatches.forEach(p -> report.append("    - ").append(p).append("\n"));
    }

    assertThat(report.toString())
        .as("OpenAPI drift detected:%s", report)
        .isEmpty();
  }

  /** Union of {@code paths} sections across every module YAML. */
  private static Map<String, Set<String>> collectYamlEndpoints() throws IOException {
    Map<String, Set<String>> all = new TreeMap<>();
    for (String file : YAML_FILES) {
      parseYamlEndpoints(file)
          .forEach(
              (path, methods) ->
                  all.merge(
                      path,
                      methods,
                      (a, b) -> {
                        Set<String> merged = new LinkedHashSet<>(a);
                        merged.addAll(b);
                        return merged;
                      }));
    }
    return all;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Set<String>> parseYamlEndpoints(String yamlFile) throws IOException {
    Path file = OPENAPI_DIR.resolve(yamlFile);
    assertThat(Files.exists(file))
        .as("Expected OpenAPI file at %s — adjust OPENAPI_DIR if the layout changed", file)
        .isTrue();
    try (var is = Files.newInputStream(file)) {
      Yaml yaml = new Yaml();
      Map<String, Object> spec = yaml.load(is);
      return extractPathsAndMethods((Map<String, Object>) spec.getOrDefault("paths", Map.of()));
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Set<String>> fetchControllerEndpoints() throws Exception {
    String json =
        mockMvc
            .perform(get("/v3/api-docs/0-all").with(authSupport.userJwt(DOC_USER_ID)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Map<String, Object> spec =
        jsonMapper.readValue(json, new TypeReference<>() {
        });
    Map<String, Set<String>> all =
        extractPathsAndMethods((Map<String, Object>) spec.getOrDefault("paths", Map.of()));
    all.keySet().removeIf(OpenApiDriftIT::isIgnoredPath);
    return all;
  }

  private static boolean isIgnoredPath(String path) {
    return IGNORED_PATH_PREFIXES.stream().anyMatch(path::startsWith);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Set<String>> extractPathsAndMethods(Map<String, Object> paths) {
    Map<String, Set<String>> result = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : paths.entrySet()) {
      Map<String, Object> operations = (Map<String, Object>) entry.getValue();
      Set<String> methods = new LinkedHashSet<>();
      for (String key : operations.keySet()) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (ALLOWED_HTTP_METHODS.contains(lower)) {
          methods.add(lower);
        }
      }
      if (!methods.isEmpty()) {
        result.put(entry.getKey(), methods);
      }
    }
    return result;
  }
}
