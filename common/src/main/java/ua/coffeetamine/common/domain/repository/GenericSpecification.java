package ua.coffeetamine.common.domain.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import ua.coffeetamine.common.domain.model.Sortable;

import lombok.experimental.UtilityClass;

/**
 * Generic specification utilities and fluent {@link SpecBuilder} for JPA Specification composition.
 *
 * <p>Centralises standard predicates ({@code eq}, {@code search}, {@code dateRange}, {@code
 * isActive}) so per-domain Spec classes stay short and declarative. The fluent {@link SpecBuilder}
 * handles null-safe AND chaining — no more {@code if (value != null) add(...)}.
 */
@UtilityClass
public class GenericSpecification {

  /** Standard {@code active} flag column shared by reference-data entities. */
  public static final String ACTIVE = "active";

  public static <T> SpecBuilder<T> spec() {
    return new SpecBuilder<>();
  }

  public static <T> SpecBuilder<T> spec(Specification<T> base) {
    return new SpecBuilder<>(base);
  }

  public static <T> Specification<T> eq(String field, Object value) {
    return (root, _, cb) -> cb.equal(root.get(field), value);
  }

  public static <T> Specification<T> byId(UUID id) {
    return eq("id", id);
  }

  public static <T> Specification<T> idIn(Collection<UUID> ids) {
    return (root, _, _) -> root.get("id").in(ids);
  }

  public static <T> Specification<T> in(String field, Collection<?> values) {
    return (root, _, _) -> root.get(field).in(values);
  }

  public static <T> Specification<T> isActive() {
    return eq(ACTIVE, true);
  }

  /** Case-insensitive ILIKE search across one or more fields (OR). */
  public static <T> Specification<T> search(String term, String... fields) {
    String pattern = "%" + term.trim().toLowerCase() + "%";
    return (root, _, cb) ->
        fields.length == 1
            ? cb.like(cb.lower(root.get(fields[0])), pattern)
            : cb.or(
                Arrays.stream(fields)
                    .map(f -> cb.like(cb.lower(root.get(f)), pattern))
                    .toArray(Predicate[]::new));
  }

  /** WHERE field between from and to. At least one bound must be non-null. */
  public static <T> Specification<T> dateRange(String field, Instant from, Instant to) {
    if (from != null && to != null) {
      return (root, _, cb) -> cb.between(root.get(field), from, to);
    }
    if (from != null) {
      return (root, _, cb) -> cb.greaterThanOrEqualTo(root.get(field), from);
    }
    return (root, _, cb) -> cb.lessThanOrEqualTo(root.get(field), to);
  }

  /**
   * Returns the next available {@code sortOrder} value (current max + 1). Uses a 1-row DESC paged
   * query — no aggregate, no native SQL.
   */
  public static <T extends Sortable> int getNextSortOrder(JpaSpecificationExecutor<T> executor) {
    return executor
        .findAll(
            (Specification<T>) (_, _, cb) -> cb.conjunction(),
            PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, Sortable.SORT_ORDER)))
        .stream()
        .findFirst()
        .map(entity -> entity.getSortOrder() + 1)
        .orElse(1);
  }

  /** Returns a pageable with {@code defaultSort} applied if the incoming pageable has no sort. */
  public static Pageable withDefaultSort(Pageable pageable, Sort defaultSort) {
    if (pageable.getSort().isSorted()) {
      return pageable;
    }
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
  }

  /**
   * Fluent builder for null-safe AND-composition of specifications.
   *
   * @param <T> entity type the composed {@link Specification} applies to
   */
  public static class SpecBuilder<T> {

    private final List<Specification<T>> specs = new ArrayList<>();

    SpecBuilder() {}

    SpecBuilder(Specification<T> base) {
      specs.add(base);
    }

    /** Adds a spec only when {@code value} is non-null. */
    public <V> SpecBuilder<T> and(V value, Function<V, Specification<T>> factory) {
      if (value != null) {
        specs.add(factory.apply(value));
      }
      return this;
    }

    /** Adds a spec only when {@code value} is non-null and non-blank. */
    public SpecBuilder<T> andIfNotBlank(String value, Function<String, Specification<T>> factory) {
      if (value != null && !value.isBlank()) {
        specs.add(factory.apply(value));
      }
      return this;
    }

    /** Adds a {@code dateRange} spec when at least one bound is non-null. */
    public SpecBuilder<T> andDateRange(String field, Instant from, Instant to) {
      if (from != null || to != null) {
        specs.add(GenericSpecification.dateRange(field, from, to));
      }
      return this;
    }

    /** Adds a spec unconditionally. */
    public SpecBuilder<T> and(Specification<T> spec) {
      specs.add(spec);
      return this;
    }

    /** Combines all added specs with AND (or {@code true} when none were added). */
    public Specification<T> build() {
      return specs.stream().reduce(Specification::and).orElse((_, _, cb) -> cb.conjunction());
    }
  }
}
