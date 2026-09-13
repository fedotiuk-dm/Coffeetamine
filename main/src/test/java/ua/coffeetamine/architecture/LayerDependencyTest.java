package ua.coffeetamine.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces DDD layer dependency rules across all Coffeetamine modules.
 *
 * <p>Layer hierarchy: {@code Controller → Service → Domain (Repository + Model)}. A higher layer
 * may depend on a lower one; never the reverse. Mappers sit beside services and must not depend on
 * controllers.
 *
 * <p>Naming conventions assumed: controllers under {@code ..web.controller..} (or {@code
 * ..controller..} fallback), JPA repositories under {@code ..domain.repository..}, entities under
 * {@code ..domain.model..}, services under {@code ..service..}, mappers under {@code ..mapper..}.
 * Stick to these when populating the feature modules.
 *
 * <p>When the feature modules are empty these rules pass trivially — they exist as guardrails for
 * when code lands.
 */
@AnalyzeClasses(packages = "ua.coffeetamine")
class LayerDependencyTest {

  private static final String[] OUR_CONTROLLER_PACKAGES = {
    "..web.controller..", "..web.controllers..", "..controller.."
  };

  /**
   * The dependency-side controller package list excludes the bare {@code ..web..} to avoid matching
   * {@code org.springframework.web.*} when used in {@code dependOnClassesThat()}.
   */
  private static final String[] CONTROLLER_DEPENDENCY_PACKAGES = {
    "..web.controller..", "..web.controllers..", "..controller.."
  };

  @ArchTest
  static final ArchRule controllers_should_not_access_repositories =
      noClasses()
          .that()
          .resideInAnyPackage(OUR_CONTROLLER_PACKAGES)
          .and()
          .haveSimpleNameEndingWith("Controller")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..domain.repository..")
          .because("Controllers must delegate to services, not access repositories directly");

  @ArchTest
  static final ArchRule controllers_should_not_use_specifications =
      noClasses()
          .that()
          .resideInAnyPackage(OUR_CONTROLLER_PACKAGES)
          .and()
          .haveSimpleNameEndingWith("Controller")
          .should()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("Spec")
          .because(
              "Controllers must not use JPA Specifications — that is service/repository layer");

  @ArchTest
  static final ArchRule domain_model_should_not_depend_on_service =
      noClasses()
          .that()
          .resideInAPackage("..domain.model..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..service..", "..services..")
          .because("Domain model must not depend on service layer");

  @ArchTest
  static final ArchRule domain_model_should_not_depend_on_web =
      noClasses()
          .that()
          .resideInAPackage("..domain.model..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(CONTROLLER_DEPENDENCY_PACKAGES)
          .because("Domain model must not depend on web layer");

  @ArchTest
  static final ArchRule domain_model_should_not_depend_on_mapper =
      noClasses()
          .that()
          .resideInAPackage("..domain.model..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..mapper..", "..mappers..")
          .because("Domain model must not depend on mapper layer");

  @ArchTest
  static final ArchRule repositories_should_not_depend_on_service =
      noClasses()
          .that()
          .resideInAPackage("..domain.repository..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..service..", "..services..")
          .because("Repositories must not depend on service layer");

  @ArchTest
  static final ArchRule repositories_should_not_depend_on_web =
      noClasses()
          .that()
          .resideInAPackage("..domain.repository..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(CONTROLLER_DEPENDENCY_PACKAGES)
          .because("Repositories must not depend on web layer");

  @ArchTest
  static final ArchRule services_should_not_depend_on_controllers =
      noClasses()
          .that()
          .resideInAnyPackage("..service..", "..services..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(CONTROLLER_DEPENDENCY_PACKAGES)
          .because("Services must not depend on controller layer");

  @ArchTest
  static final ArchRule mappers_should_not_depend_on_controllers =
      noClasses()
          .that()
          .resideInAnyPackage("..mapper..", "..mappers..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(CONTROLLER_DEPENDENCY_PACKAGES)
          .because("Mappers must not depend on controller layer");
}
