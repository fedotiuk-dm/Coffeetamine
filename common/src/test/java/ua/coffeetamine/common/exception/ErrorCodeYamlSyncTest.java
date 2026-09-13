package ua.coffeetamine.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Guards against drift between {@link ErrorCode} (Java source of truth for codes used at runtime)
 * and the {@code errorCode.enum} list advertised in {@code openapi/common.yaml} (contract surface
 * consumed by FE codegen, Swagger UI and contract tests).
 *
 * <p>If this test fails, either the Java enum got a new entry that wasn't mirrored into the YAML,
 * or the YAML lists a code that no longer exists in Java — both are breakages of the public
 * contract that we want to catch in CI rather than discover via support tickets.
 */
class ErrorCodeYamlSyncTest {

  private static final Path COMMON_YAML =
      Paths.get(System.getProperty("user.dir")).resolveSibling("openapi").resolve("common.yaml");

  @Test
  @DisplayName("ErrorCode.values() ↔ openapi/common.yaml errorCode.enum stay in sync")
  void errorCodeEnumIsMirroredInOpenApiSpec() throws IOException {
    Set<String> javaCodes =
        Arrays.stream(ErrorCode.values())
            .map(ErrorCode::getCode)
            .collect(Collectors.toCollection(TreeSet::new));
    Set<String> yamlCodes = new TreeSet<>(loadErrorCodeEnumFromYaml());

    Set<String> missingInYaml = new TreeSet<>(javaCodes);
    missingInYaml.removeAll(yamlCodes);
    Set<String> missingInJava = new TreeSet<>(yamlCodes);
    missingInJava.removeAll(javaCodes);

    assertThat(missingInYaml)
        .as(
            "Codes present in ErrorCode.java but missing from openapi/common.yaml "
                + "errorCode.enum — add them to the YAML so FE codegen and Swagger UI see them.")
        .isEmpty();
    assertThat(missingInJava)
        .as(
            "Codes listed in openapi/common.yaml errorCode.enum but missing from ErrorCode.java — "
                + "either remove from YAML, or add the matching enum constant.")
        .isEmpty();
  }

  @SuppressWarnings("unchecked")
  private List<String> loadErrorCodeEnumFromYaml() throws IOException {
    assertThat(COMMON_YAML)
        .as("openapi/common.yaml must exist relative to the common module: %s", COMMON_YAML)
        .exists();
    Map<String, Object> root;
    try (var in = Files.newInputStream(COMMON_YAML)) {
      root = new Yaml().load(in);
    }
    Map<String, Object> components = (Map<String, Object>) root.get("components");
    Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
    Map<String, Object> errorResponse = (Map<String, Object>) schemas.get("ErrorResponse");
    Map<String, Object> properties = (Map<String, Object>) errorResponse.get("properties");
    Map<String, Object> errorCode = (Map<String, Object>) properties.get("errorCode");
    Object enumNode = errorCode.get("enum");
    assertThat(enumNode)
        .as(
            "ErrorResponse.properties.errorCode must declare an `enum:` list so the contract "
                + "exposes the full code set to clients.")
        .isInstanceOf(List.class);
    return (List<String>) enumNode;
  }
}
