package ua.coffeetamine.common.spi;

import java.util.UUID;

/**
 * Projection of an interest tag exposed to other modules. Carries only the fields needed for
 * discovery / display — not the full {@code InterestTag} entity.
 */
public record InterestSummary(
    UUID id, String code, String displayName, String category, Integer sortOrder) {}
