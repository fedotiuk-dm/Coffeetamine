package ua.coffeetamine.ping.mapper;

import ua.coffeetamine.common.spi.UserProfileSummary;
import ua.coffeetamine.ping.domain.model.UserMatch;

/**
 * Composite view that pairs a {@link UserMatch} row with the peer user's public profile summary.
 * Built by the service from cross-module lookups; consumed by {@link PingMapper#toResponse}.
 */
public record MatchView(UserMatch match, UserProfileSummary peer) {}
