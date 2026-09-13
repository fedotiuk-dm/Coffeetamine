package ua.coffeetamine.common.domain.model;

/**
 * Marker interface for entities that support display ordering via a {@code sortOrder} column. Pairs
 * with {@link ua.coffeetamine.common.domain.repository.SortableRepository} which exposes a {@code
 * getNextSortOrder()} helper to auto-assign the next free slot.
 */
public interface Sortable {

  /**
   * JPA attribute name for the {@code sortOrder} field — used in {@code Sort} and Specifications.
   */
  String SORT_ORDER = "sortOrder";

  int getSortOrder();

  void setSortOrder(int sortOrder);
}
