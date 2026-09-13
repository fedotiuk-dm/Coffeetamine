package ua.coffeetamine.common.spi;

import java.net.URI;

import org.jspecify.annotations.Nullable;

/** Projection of a {@code MoodBoardImageRef} exposed to other modules (e.g. discovery). */
public record MoodBoardImageSummary(
    String unsplashPhotoId,
    URI imageUrl,
    String photographerName,
    @Nullable URI photographerProfileUrl,
    URI attributionUrl) {}
