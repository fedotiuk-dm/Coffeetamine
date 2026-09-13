package ua.coffeetamine.moodboard.domain.model;

import java.util.List;

/**
 * Immutable wrapper around the mood-board image list so the JSONB attribute has a concrete,
 * non-parameterized type. Keeps the Hibernate static metamodel fully typed.
 */
public record MoodBoardImages(List<MoodBoardImageRef> images) {}
