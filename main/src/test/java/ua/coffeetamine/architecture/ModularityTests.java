package ua.coffeetamine.architecture;

import org.springframework.modulith.core.ApplicationModules;

import ua.coffeetamine.CoffeetamineApplication;

import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.Test;

/**
 * Spring Modulith boundary verification — the source of truth for cross-module access. Each feature
 * module may only reach another module through {@code common} (the OPEN shared kernel: {@code spi}
 * read ports + {@code event} records). Two package trees are excluded from the module model because
 * they are not business modules: {@code ua.coffeetamine.config} (the composition root that wires
 * everything) and {@code ua.coffeetamine.api} (generated OpenAPI DTOs — the shared HTTP contract).
 *
 * <p>Inspect the live module graph at runtime via {@code GET /actuator/modulith}.
 */
class ModularityTests {

  static final ApplicationModules modules =
      ApplicationModules.of(
          CoffeetamineApplication.class,
          JavaClass.Predicates.resideInAPackage("ua.coffeetamine.config..")
              .or(JavaClass.Predicates.resideInAPackage("ua.coffeetamine.api..")));

  @Test
  void verifiesModuleBoundaries() {
    modules.verify();
  }
}
