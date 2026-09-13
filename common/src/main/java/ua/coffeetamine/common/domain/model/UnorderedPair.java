package ua.coffeetamine.common.domain.model;

import java.util.UUID;

/**
 * Canonical unordered pair of UUIDs, sorted so {@code low.compareTo(high) <= 0}. Use when modelling
 * symmetric relationships (matches, friendships, ping pairs) where direction is irrelevant — the
 * sorted form is what unique constraints index on.
 */
public record UnorderedPair(UUID low, UUID high) {

  public static UnorderedPair of(UUID a, UUID b) {
    return a.compareTo(b) <= 0 ? new UnorderedPair(a, b) : new UnorderedPair(b, a);
  }
}
