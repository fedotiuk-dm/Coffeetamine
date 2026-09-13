package ua.coffeetamine.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Guards the authorization model: {@code SecurityFilterChain} only enforces the public /
 * authenticated boundary, so every REST controller handler MUST carry its own explicit
 * authorization decision — a role via {@code @RequiresAdmin}, plain authentication via
 * {@code @RequiresAuthenticated}, or {@code @PublicEndpoint}.
 *
 * <p>All three meta-annotations resolve to Spring's {@code @PreAuthorize}, so a missing annotation
 * (on both the method and its declaring class) means the endpoint has no authorization decision at
 * all — which this test fails on.
 */
@AnalyzeClasses(packages = "ua.coffeetamine")
class ControllerAuthorizationTest {

  private static boolean hasAuthorization(JavaClass type) {
    return type.isAnnotatedWith(PreAuthorize.class) || type.isMetaAnnotatedWith(PreAuthorize.class);
  }

  private static final ArchCondition<JavaMethod> DECLARE_AUTHORIZATION =
      new ArchCondition<>(
          "declare an authorization annotation (@RequiresAdmin / @RequiresAuthenticated / "
              + "@PublicEndpoint / @PreAuthorize) on the method or its controller") {
        @Override
        public void check(JavaMethod method, ConditionEvents events) {
          boolean decided =
              method.isAnnotatedWith(PreAuthorize.class)
                  || method.isMetaAnnotatedWith(PreAuthorize.class)
                  || hasAuthorization(method.getOwner());
          if (!decided) {
            events.add(
                SimpleConditionEvent.violated(
                    method,
                    method.getFullName()
                        + " has no authorization annotation — add "
                        + "@RequiresAdmin / @RequiresAuthenticated / @PublicEndpoint"));
          }
        }
      };

  @ArchTest
  static final ArchRule controller_handlers_must_declare_authorization =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .areAnnotatedWith(RestController.class)
          .and()
          .arePublic()
          .and()
          .areNotStatic()
          .should(DECLARE_AUTHORIZATION)
          .because(
              "SecurityFilterChain only enforces the public/authenticated boundary — "
                  + "role and ownership checks must live next to the code they guard");
}
