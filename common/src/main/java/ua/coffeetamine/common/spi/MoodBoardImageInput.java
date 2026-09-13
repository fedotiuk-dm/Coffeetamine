package ua.coffeetamine.common.spi;

import java.net.URI;

/**
 * Stable cross-module image input record. Separate from the generated {@code
 * ua.coffeetamine.api.moodboard.dto.MoodBoardImageInput} so callers don't take a transitive
 * compile-time dependency on the moodboard OpenAPI generation. Fields mirror the API schema.
 */
public record MoodBoardImageInput(
    String unsplashPhotoId,
    URI imageUrl,
    String photographerName,
    URI photographerProfileUrl,
    URI attributionUrl) {}
