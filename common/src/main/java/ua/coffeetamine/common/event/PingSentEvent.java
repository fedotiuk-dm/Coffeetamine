package ua.coffeetamine.common.event;

import java.util.UUID;

/**
 * Published right after a PENDING ping row is persisted. Consumer: notification (PING_RECEIVED).
 */
public record PingSentEvent(UUID pingId, UUID fromUserId, UUID toUserId) {}
