package ua.coffeetamine.common.spi;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Projection of a Ready user's jittered presence exposed to other modules. Coordinates are already
 * jittered server-side — raw GPS never leaves the presence module. Carries {@code status} and
 * {@code updatedAt} so cross-module callers building presence-style response DTOs don't need to
 * re-fetch the underlying row. Lat/lng are primitives — this projection is only built for rows with
 * a non-null jittered location, so nullable coordinates would be a contract violation.
 */
public record NearbyPresence(
    UUID userId,
    PresenceStatus status,
    double latitude,
    double longitude,
    @Nullable String mood,
    Instant updatedAt) {}
