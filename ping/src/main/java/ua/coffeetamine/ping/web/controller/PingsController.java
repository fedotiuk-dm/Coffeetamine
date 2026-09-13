package ua.coffeetamine.ping.web.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import ua.coffeetamine.api.ping.PingsApi;
import ua.coffeetamine.api.ping.dto.Match;
import ua.coffeetamine.api.ping.dto.Ping;
import ua.coffeetamine.api.ping.dto.PingListResponse;
import ua.coffeetamine.api.ping.dto.PingStatus;
import ua.coffeetamine.api.ping.dto.SendPingRequest;
import ua.coffeetamine.common.security.RequiresAuthenticated;
import ua.coffeetamine.ping.service.PingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequiresAuthenticated
public class PingsController implements PingsApi {

  private final PingService service;

  @Override
  public ResponseEntity<Ping> sendPing(SendPingRequest sendPingRequest) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.sendPing(sendPingRequest));
  }

  @Override
  public ResponseEntity<PingListResponse> listSentPings(PingStatus status, Pageable pageable) {
    return ResponseEntity.ok(service.listSentPings(status, pageable));
  }

  @Override
  public ResponseEntity<PingListResponse> listReceivedPings(PingStatus status, Pageable pageable) {
    return ResponseEntity.ok(service.listReceivedPings(status, pageable));
  }

  @Override
  public ResponseEntity<Void> withdrawPing(UUID pingId) {
    service.withdrawPing(pingId);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Match> pongPing(UUID pingId) {
    return ResponseEntity.ok(service.pongPing(pingId));
  }
}
