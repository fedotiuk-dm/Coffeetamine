package ua.coffeetamine.discovery.readmodel;

import java.util.List;

import ua.coffeetamine.common.spi.InterestSummary;
import ua.coffeetamine.common.spi.MoodBoardSummary;
import ua.coffeetamine.common.spi.NearbyPresence;
import ua.coffeetamine.common.spi.UserProfileSummary;

public record DiscoveryUserDetailView(
    UserProfileSummary profile,
    List<InterestSummary> interests,
    MoodBoardSummary moodBoard,
    NearbyPresence presence,
    Integer compatibilityScore,
    Integer distanceMeters) {}
