package ua.coffeetamine.moodboard.domain.model;

import java.net.URI;

/**
 * Immutable reference to an Unsplash image stored on a {@link MoodBoard}. Persisted as part of the
 * {@code mood_boards.images} JSONB column — Hibernate's {@code SqlTypes.JSON} adapter uses Jackson,
 * which serialises Java records natively.
 *
 * <p>Read-only by design: replace the whole list to "edit" a board, never mutate an existing ref.
 */
public record MoodBoardImageRef(
    String unsplashPhotoId,
    URI imageUrl,
    String photographerName,
    URI photographerProfileUrl,
    URI attributionUrl) {}
