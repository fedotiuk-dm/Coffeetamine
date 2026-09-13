package ua.coffeetamine.ping.web.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import ua.coffeetamine.api.ping.MatchesApi;
import ua.coffeetamine.api.ping.dto.Match;
import ua.coffeetamine.api.ping.dto.MatchListResponse;
import ua.coffeetamine.common.security.RequiresAuthenticated;
import ua.coffeetamine.ping.service.PingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequiresAuthenticated
public class MatchesController implements MatchesApi {

  private final PingService service;

  @Override
  public ResponseEntity<MatchListResponse> listMatches(Pageable pageable) {
    return ResponseEntity.ok(service.listMatches(pageable));
  }

  @Override
  public ResponseEntity<Match> getMatch(UUID matchId) {
    return ResponseEntity.ok(service.getMatch(matchId));
  }
}
