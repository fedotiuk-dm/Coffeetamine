package ua.coffeetamine.ping.service;

import java.util.UUID;

import org.springframework.data.domain.Pageable;

import ua.coffeetamine.api.ping.dto.Match;
import ua.coffeetamine.api.ping.dto.MatchListResponse;
import ua.coffeetamine.api.ping.dto.Ping;
import ua.coffeetamine.api.ping.dto.PingListResponse;
import ua.coffeetamine.api.ping.dto.PingStatus;
import ua.coffeetamine.api.ping.dto.SendPingRequest;

public interface PingService {

  Ping sendPing(SendPingRequest request);

  PingListResponse listSentPings(PingStatus status, Pageable pageable);

  PingListResponse listReceivedPings(PingStatus status, Pageable pageable);

  void withdrawPing(UUID pingId);

  Match pongPing(UUID pingId);

  MatchListResponse listMatches(Pageable pageable);

  Match getMatch(UUID matchId);
}
