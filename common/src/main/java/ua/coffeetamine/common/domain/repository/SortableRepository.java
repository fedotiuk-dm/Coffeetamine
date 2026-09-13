package ua.coffeetamine.common.domain.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import ua.coffeetamine.common.domain.model.Sortable;

/**
 * Repository mixin for entities that support {@code sortOrder}. Provides auto-assignment of the
 * next available {@code sortOrder} value via {@link GenericSpecification#getNextSortOrder}.
 *
 * <p>Use by adding {@code SortableRepository<MyEntity>} to a repository's extends clause — the
 * entity itself must implement {@link Sortable}.
 *
 * @param <T> entity type that implements {@link Sortable}
 */
@NoRepositoryBean
public interface SortableRepository<T extends Sortable> extends JpaSpecificationExecutor<T> {

  /** Returns the next available sortOrder (current max + 1, starting from 1). */
  default int getNextSortOrder() {
    return GenericSpecification.getNextSortOrder(this);
  }
}
