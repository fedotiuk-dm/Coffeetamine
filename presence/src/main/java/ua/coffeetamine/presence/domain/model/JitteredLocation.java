package ua.coffeetamine.presence.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Server-side jittered coordinates persisted alongside {@link UserPresence}. Immutable by design —
 * raw GPS is never echoed back through the API (CLAUDE.md invariant).
 */
@Embeddable
public record JitteredLocation(
    @Column(name = "latitude") Double latitude, @Column(name = "longitude") Double longitude) {}
