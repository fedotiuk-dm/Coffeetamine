package ua.coffeetamine.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.Version;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Enforces JPA entity cleanliness rules.
 *
 * <p>Entities must be pure data holders with JPA annotations; business logic belongs in services.
 * Mutable entities must extend {@code BaseAuditableEntity} (which already supplies {@code @Version}
 * and {@code @CreationTimestamp}); append-only entities listed in {@link #INFRASTRUCTURE_ENTITIES}
 * may extend the immutable {@code BaseEntity}.
 */
@AnalyzeClasses(packages = "ua.coffeetamine")
class CleanEntityTest {

  @ArchTest
  static final ArchRule entities_should_not_use_formula =
      noClasses()
          .that()
          .areAnnotatedWith(Entity.class)
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.hibernate.annotations.Formula")
          .because("Entities must be clean — use domain methods instead of @Formula");

  @ArchTest
  static final ArchRule entities_should_not_depend_on_services =
      noClasses()
          .that()
          .areAnnotatedWith(Entity.class)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..service..", "..services..")
          .because("Entities must not depend on service layer");

  @ArchTest
  static final ArchRule entities_should_not_depend_on_web =
      noClasses()
          .that()
          .areAnnotatedWith(Entity.class)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..web.controller..", "..web.controllers..", "..controller..")
          .because("Entities must not depend on web layer");

  @ArchTest
  static final ArchRule entities_should_not_depend_on_mappers =
      noClasses()
          .that()
          .areAnnotatedWith(Entity.class)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..mapper..", "..mappers..")
          .because("Entities must not depend on mapper layer");

  @ArchTest
  static final ArchRule entities_should_have_version_field =
      classes()
          .that()
          .areAnnotatedWith(Entity.class)
          .and(areNotExcludedFromVersionCheck())
          .should(haveFieldAnnotatedWith(Version.class))
          .because(
              "All mutable entities must have @Version for optimistic locking — extend "
                  + "BaseAuditableEntity, or add the entity to INFRASTRUCTURE_ENTITIES if "
                  + "it is genuinely append-only");

  @ArchTest
  static final ArchRule entities_should_have_creation_timestamp =
      classes()
          .that()
          .areAnnotatedWith(Entity.class)
          .and(areNotExcludedFromVersionCheck())
          .should(haveFieldAnnotatedWith(org.hibernate.annotations.CreationTimestamp.class))
          .because("All entities must have @CreationTimestamp for audit trail");

  /**
   * Infrastructure / append-only entities that legitimately skip {@code @Version}.
   *
   * <p>{@code Match} is created exactly once per unordered (a, b) pair and never updated. Add
   * future append-only entities (notification deliveries, outbox events) here as they land.
   */
  private static final List<String> INFRASTRUCTURE_ENTITIES = List.of("Match");

  private static DescribedPredicate<JavaClass> areNotExcludedFromVersionCheck() {
    return new DescribedPredicate<>(
        "are not @Embeddable, @MappedSuperclass, or infrastructure entities") {
      @Override
      public boolean test(JavaClass javaClass) {
        return !javaClass.isAnnotatedWith(jakarta.persistence.Embeddable.class)
            && !javaClass.isAnnotatedWith(jakarta.persistence.MappedSuperclass.class)
            && !INFRASTRUCTURE_ENTITIES.contains(javaClass.getSimpleName());
      }
    };
  }

  private static ArchCondition<JavaClass> haveFieldAnnotatedWith(
      Class<? extends java.lang.annotation.Annotation> annotationType) {
    return new ArchCondition<>("have a field annotated with @" + annotationType.getSimpleName()) {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        boolean found =
            javaClass.getAllFields().stream()
                .anyMatch(field -> isAnnotatedDirectlyOrViaLombok(field, annotationType));
        if (!found) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  String.format(
                      "%s does not have a field annotated with @%s",
                      javaClass.getName(), annotationType.getSimpleName())));
        }
      }
    };
  }

  private static boolean isAnnotatedDirectlyOrViaLombok(
      JavaField field, Class<? extends java.lang.annotation.Annotation> annotationType) {
    return field.isAnnotatedWith(annotationType);
  }
}
