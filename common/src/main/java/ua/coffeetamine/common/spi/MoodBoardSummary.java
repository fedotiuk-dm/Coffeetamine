package ua.coffeetamine.common.spi;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Projection of a user's mood board (up to 6 Unsplash references) for cross-module reads. */
public record MoodBoardSummary(
    UUID userId, List<MoodBoardImageSummary> images, Instant updatedAt) {}
