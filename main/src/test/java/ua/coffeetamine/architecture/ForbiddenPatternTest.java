package ua.coffeetamine.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Forbids usage of patterns that violate project conventions.
 *
 * <p>These rules catch common mistakes early: console output, legacy date APIs, wrong exception
 * types. Checkstyle catches some of these too at the source-text level — ArchUnit catches them at
 * the compiled-class level, which closes a few gaps (e.g. references through library APIs).
 */
@AnalyzeClasses(packages = "ua.coffeetamine")
class ForbiddenPatternTest {

  @ArchTest
  static final ArchRule no_system_out_or_err =
      noClasses()
          .that()
          .resideInAPackage("ua.coffeetamine..")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("java.io.PrintStream")
          .because("Use @Slf4j logging instead of System.out / System.err");

  @ArchTest
  static final ArchRule no_java_util_date =
      noClasses()
          .that()
          .resideInAPackage("ua.coffeetamine..")
          .and()
          .haveSimpleNameNotEndingWith("MapperImpl")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("java.util.Date")
          .because("Use java.time.Instant instead of java.util.Date");

  @ArchTest
  static final ArchRule no_java_util_calendar =
      noClasses()
          .that()
          .resideInAPackage("ua.coffeetamine..")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("java.util.Calendar")
          .because("Use the java.time API instead of java.util.Calendar");

  @ArchTest
  static final ArchRule no_throwing_illegal_state_exception =
      noClasses()
          .that()
          .resideInAPackage("ua.coffeetamine..")
          .should(constructIllegalStateException())
          .because(
              "Use ConflictException (409) instead of IllegalStateException (500) for business rule violations");

  @ArchTest
  static final ArchRule no_keycloak_named_types =
      noClasses()
          .that()
          .resideInAPackage("ua.coffeetamine..")
          .should()
          .haveSimpleNameStartingWith("Keycloak")
          .because(
              "Keycloak-specific names were renamed to provider-neutral equivalents "
                  + "(AuthenticatedUser / AppUserKeyedEntity) to keep the backend OIDC-agnostic. "
                  + "Re-introducing them is a regression — add new neutral abstractions instead.");

  private static ArchCondition<JavaClass> constructIllegalStateException() {
    return new ArchCondition<>("construct " + IllegalStateException.class.getSimpleName()) {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass.getConstructorCallsFromSelf().stream()
            .filter(call -> call.getTargetOwner().isEquivalentTo(IllegalStateException.class))
            .forEach(
                call ->
                    events.add(
                        SimpleConditionEvent.violated(
                            javaClass,
                            String.format(
                                "%s constructs %s at %s",
                                javaClass.getName(),
                                IllegalStateException.class.getSimpleName(),
                                call.getSourceCodeLocation()))));
      }
    };
  }
}
