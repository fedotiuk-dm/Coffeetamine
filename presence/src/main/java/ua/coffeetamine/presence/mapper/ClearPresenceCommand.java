package ua.coffeetamine.presence.mapper;

import ua.coffeetamine.presence.domain.model.JitteredLocation;
import ua.coffeetamine.presence.domain.model.PresenceState;

import org.jspecify.annotations.Nullable;

public record ClearPresenceCommand(
    PresenceState status, @Nullable String mood, @Nullable JitteredLocation location) {

  public static ClearPresenceCommand notReady() {
    return new ClearPresenceCommand(PresenceState.NOT_READY, null, null);
  }
}
