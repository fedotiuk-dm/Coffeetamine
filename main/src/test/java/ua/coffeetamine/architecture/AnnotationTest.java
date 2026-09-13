package ua.coffeetamine.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces annotation usage patterns across all modules.
 *
 * <p>Key rules: no field injection, correct stereotype annotations on controllers and service
 * implementations. MapStruct-generated {@code *MapperImpl} classes are exempt from the field
 * injection rule — generated code uses {@code @Autowired} legitimately.
 */
@AnalyzeClasses(packages = "ua.coffeetamine")
class AnnotationTest {

  @ArchTest
  static final ArchRule no_field_injection =
      noFields()
          .that()
          .areDeclaredInClassesThat()
          .haveSimpleNameNotEndingWith("MapperImpl")
          .and()
          .areDeclaredInClassesThat()
          .resideOutsideOfPackage("..integration..")
          .should()
          .beAnnotatedWith(Autowired.class)
          .because(
              "Field injection is forbidden — use constructor injection via @RequiredArgsConstructor");

  @ArchTest
  static final ArchRule controllers_must_be_annotated_with_rest_controller =
      classes()
          .that()
          .resideInAnyPackage("..web.controller..", "..web.controllers..", "..controller..")
          .and()
          .haveSimpleNameEndingWith("Controller")
          .and()
          .areNotInterfaces()
          .should()
          .beAnnotatedWith(RestController.class)
          .because("All REST controller classes must be annotated with @RestController");

  @ArchTest
  static final ArchRule service_implementations_must_be_annotated_with_service =
      classes()
          .that()
          .resideInAnyPackage("..service..", "..services..")
          .and()
          .haveSimpleNameEndingWith("ServiceImpl")
          .should()
          .beAnnotatedWith(Service.class)
          .because("Service implementations must be annotated with @Service");
}
