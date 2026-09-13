package ua.coffeetamine.ping.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.api.ping.dto.Match;
import ua.coffeetamine.api.ping.dto.MatchListResponse;
import ua.coffeetamine.api.ping.dto.Ping;
import ua.coffeetamine.api.ping.dto.PingListResponse;
import ua.coffeetamine.api.ping.dto.PingStatus;
import ua.coffeetamine.api.ping.dto.SendPingRequest;
import ua.coffeetamine.common.domain.model.UnorderedPair;
import ua.coffeetamine.common.event.PingSentEvent;
import ua.coffeetamine.common.exception.BadRequestException;
import ua.coffeetamine.common.exception.ConflictException;
import ua.coffeetamine.common.exception.ErrorCode;
import ua.coffeetamine.common.exception.ForbiddenException;
import ua.coffeetamine.common.exception.NotFoundException;
import ua.coffeetamine.common.security.SecurityUtils;
import ua.coffeetamine.common.security.UserOnboardingGuard;
import ua.coffeetamine.common.spi.PresenceLookup;
import ua.coffeetamine.common.spi.UserProfileLookup;
import ua.coffeetamine.common.spi.UserProfileSummary;
import ua.coffeetamine.ping.domain.model.PingInteraction;
import ua.coffeetamine.ping.domain.model.PingState;
import ua.coffeetamine.ping.domain.model.UserMatch;
import ua.coffeetamine.ping.domain.repository.PingInteractionRepository;
import ua.coffeetamine.ping.domain.repository.UserMatchRepository;
import ua.coffeetamine.ping.mapper.MatchView;
import ua.coffeetamine.ping.mapper.PingMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PingServiceImpl implements PingService {

  private final PingInteractionRepository pingRepository;
  private final UserMatchRepository matchRepository;
  private final UserProfileLookup userProfileLookup;
  private final PresenceLookup presenceLookup;
  private final PingStatusManager statusManager;
  private final ApplicationEventPublisher eventPublisher;
  private final PingMapper mapper;
  private final UserOnboardingGuard onboardingGuard;
  private final SecurityUtils securityUtils;

  @Override
  @Transactional
  public Ping sendPing(SendPingRequest request) {
    UUID callerId = securityUtils.getCurrentUserId();
    UUID targetUserId = request.getTargetUserId();
    if (callerId.equals(targetUserId)) {
      throw new BadRequestException(ErrorCode.PING_SELF_TARGET, "Cannot ping yourself");
    }
    onboardingGuard.requireOnboardingComplete(callerId);
    if (!presenceLookup.isReady(targetUserId)) {
      throw new ConflictException(ErrorCode.PING_TARGET_NOT_READY, "Target user is not Ready");
    }

    try {
      PingInteraction ping =
          pingRepository.saveAndFlush(
              mapper.toNewPing(callerId, targetUserId, UnorderedPair.of(callerId, targetUserId)));
      eventPublisher.publishEvent(new PingSentEvent(ping.getId(), callerId, targetUserId));
      return mapper.toResponse(ping);
    } catch (DataIntegrityViolationException _) {
      throw new ConflictException(ErrorCode.PING_ALREADY_SENT, "Pending ping already exists");
    }
  }

  @Override
  public PingListResponse listSentPings(PingStatus status, Pageable pageable) {
    return mapper.toPingListResponse(
        pingRepository.findSent(
            securityUtils.getCurrentUserId(), mapper.toState(status), pageable));
  }

  @Override
  public PingListResponse listReceivedPings(PingStatus status, Pageable pageable) {
    return mapper.toPingListResponse(
        pingRepository.findReceived(
            securityUtils.getCurrentUserId(), mapper.toState(status), pageable));
  }

  @Override
  @Transactional
  public void withdrawPing(UUID pingId) {
    PingInteraction ping = requirePing(pingId);
    securityUtils.verifyOwnerOrAdmin(ping.getFromUserId());
    statusManager.withdraw(ping);
  }

  @Override
  @Transactional
  public Match pongPing(UUID pingId) {
    UUID callerId = securityUtils.getCurrentUserId();
    PingInteraction ping =
        pingRepository
            .findByIdForUpdate(pingId)
            .orElseThrow(() -> new NotFoundException("Ping", pingId));
    if (!ping.getToUserId().equals(callerId)) {
      throw new ForbiddenException(ErrorCode.AUTH_ACCESS_DENIED, "Only ping recipient can pong it");
    }
    UserMatch match =
        ping.getStatus() == PingState.MATCHED ? requireMatch(ping) : createMatch(ping);
    statusManager.match(ping, match.getId());
    return mapper.toResponse(toMatchView(match, callerId));
  }

  @Override
  public MatchListResponse listMatches(Pageable pageable) {
    UUID callerId = securityUtils.getCurrentUserId();
    Page<UserMatch> matches = matchRepository.findByParticipant(callerId, pageable);

    Map<UUID, UserProfileSummary> peers =
        userProfileLookup.findAllByIds(matches.stream().map(m -> m.peerOf(callerId)).toList());

    return mapper.toMatchListResponse(
        matches.map(m -> new MatchView(m, requirePeer(peers, m.peerOf(callerId)))));
  }

  @Override
  public Match getMatch(UUID matchId) {
    UUID callerId = securityUtils.getCurrentUserId();
    UserMatch match =
        matchRepository
            .findById(matchId)
            .filter(m -> m.involves(callerId))
            .orElseThrow(() -> new NotFoundException("Match", matchId));
    return mapper.toResponse(toMatchView(match, callerId));
  }

  private UserMatch createMatch(PingInteraction ping) {
    return matchRepository
        .findByPair(ping.getFromUserId(), ping.getToUserId())
        .orElseGet(
            () ->
                matchRepository.save(
                    mapper.toNewMatch(ping.getFromUserId(), ping.getToUserId(), ping.getId())));
  }

  private PingInteraction requirePing(UUID pingId) {
    return pingRepository.findById(pingId).orElseThrow(() -> new NotFoundException("Ping", pingId));
  }

  private UserMatch requireMatch(PingInteraction ping) {
    return matchRepository
        .findByPair(ping.getFromUserId(), ping.getToUserId())
        .orElseThrow(() -> new NotFoundException("Match", ping.getId()));
  }

  private MatchView toMatchView(UserMatch match, UUID callerId) {
    UUID peerId = match.peerOf(callerId);
    UserProfileSummary peer =
        userProfileLookup
            .findById(peerId)
            .orElseThrow(() -> new NotFoundException("UserProfile", peerId));
    return new MatchView(match, peer);
  }

  private static UserProfileSummary requirePeer(Map<UUID, UserProfileSummary> peers, UUID peerId) {
    return Optional.ofNullable(peers.get(peerId))
        .orElseThrow(() -> new NotFoundException("UserProfile", peerId));
  }
}
