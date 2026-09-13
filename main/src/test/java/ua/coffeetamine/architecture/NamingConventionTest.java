package ua.coffeetamine.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces naming conventions across all Coffeetamine modules.
 *
 * <p>Consistent suffixes (* Controller, *Repository, *Mapper, *Properties) keep the codebase
 * navigable and let other ArchUnit rules target classes by name pattern without false positives.
 */
@AnalyzeClasses(packages = "ua.coffeetamine")
class NamingConventionTest {

  @ArchTest
  static final ArchRule controllers_should_end_with_controller =
      classes()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .haveSimpleNameEndingWith("Controller")
          .because("REST controllers must follow the naming convention *Controller");

  @ArchTest
  static final ArchRule classes_named_controller_should_be_in_controller_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .and()
          .areAnnotatedWith(RestController.class)
          .should()
          .resideInAnyPackage("..web.controller..", "..web.controllers..", "..controller..")
          .because("Controllers must live in a controller package");

  @ArchTest
  static final ArchRule repository_interfaces_should_end_with_repository =
      classes()
          .that()
          .resideInAPackage("..domain.repository..")
          .and()
          .areInterfaces()
          .and()
          .haveSimpleNameNotEndingWith("Spec")
          .and()
          .haveSimpleNameNotEndingWith("Projection")
          .should()
          .haveSimpleNameEndingWith("Repository")
          .because("Repository interfaces must follow the naming convention *Repository");

  @ArchTest
  static final ArchRule mapper_interfaces_should_end_with_mapper =
      classes()
          .that()
          .resideInAnyPackage("..mapper..", "..mappers..")
          .and()
          .areInterfaces()
          .should()
          .haveSimpleNameEndingWith("Mapper")
          .because("MapStruct mapper interfaces must follow the naming convention *Mapper");

  @ArchTest
  static final ArchRule configuration_properties_should_end_with_properties =
      classes()
          .that()
          .areAnnotatedWith(ConfigurationProperties.class)
          .should()
          .haveSimpleNameEndingWith("Properties")
          .allowEmptyShould(true)
          .because(
              "@ConfigurationProperties classes must follow the naming convention *Properties");
}
