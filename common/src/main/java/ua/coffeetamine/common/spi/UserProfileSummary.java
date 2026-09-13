package ua.coffeetamine.common.spi;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Public projection of {@code UserProfile} exposed to other modules. Carries the fields any module
 * may need to display a user (name, avatar, bio) — not the full entity.
 */
public record UserProfileSummary(
    UUID userId, String name, @Nullable String avatarUrl, @Nullable String about) {}
