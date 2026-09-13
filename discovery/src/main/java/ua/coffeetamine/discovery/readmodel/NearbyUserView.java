package ua.coffeetamine.discovery.readmodel;

import ua.coffeetamine.common.spi.NearbyPresence;
import ua.coffeetamine.common.spi.UserProfileSummary;

public record NearbyUserView(
    UserProfileSummary profile,
    NearbyPresence presence,
    Integer compatibilityScore,
    Integer distanceMeters) {}
