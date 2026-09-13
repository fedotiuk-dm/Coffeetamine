package ua.coffeetamine.common.event;

import java.util.UUID;

/**
 * Published when a ping transitions to MATCHED and a UserMatch row exists. Consumer: notification
 * (MATCH_CREATED — fans out to both participants).
 */
public record PingMatchedEvent(UUID matchId, UUID participantLowId, UUID participantHighId) {}
