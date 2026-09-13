package ua.coffeetamine.presence.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import ua.coffeetamine.api.presence.PresenceApi;
import ua.coffeetamine.api.presence.dto.PresenceResponse;
import ua.coffeetamine.api.presence.dto.UpdatePresenceRequest;
import ua.coffeetamine.common.security.RequiresAuthenticated;
import ua.coffeetamine.presence.service.PresenceService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequiresAuthenticated
public class PresenceController implements PresenceApi {

  private final PresenceService service;

  @Override
  public ResponseEntity<PresenceResponse> getMyPresence() {
    return ResponseEntity.ok(service.getMyPresence());
  }

  @Override
  public ResponseEntity<PresenceResponse> updateMyPresence(UpdatePresenceRequest body) {
    return ResponseEntity.ok(service.updateMyPresence(body));
  }

  @Override
  public ResponseEntity<Void> clearMyPresence() {
    service.clearMyPresence();
    return ResponseEntity.noContent().build();
  }
}
