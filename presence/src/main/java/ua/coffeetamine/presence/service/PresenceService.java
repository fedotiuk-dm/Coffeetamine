package ua.coffeetamine.presence.service;

import ua.coffeetamine.api.presence.dto.PresenceResponse;
import ua.coffeetamine.api.presence.dto.UpdatePresenceRequest;

public interface PresenceService {

  PresenceResponse getMyPresence();

  PresenceResponse updateMyPresence(UpdatePresenceRequest request);

  void clearMyPresence();
}
