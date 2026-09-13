package ua.coffeetamine.ping.mapper;

import java.util.UUID;

import org.springframework.data.domain.Page;

import ua.coffeetamine.api.ping.dto.Match;
import ua.coffeetamine.api.ping.dto.MatchListResponse;
import ua.coffeetamine.api.ping.dto.Ping;
import ua.coffeetamine.api.ping.dto.PingListResponse;
import ua.coffeetamine.api.ping.dto.PingStatus;
import ua.coffeetamine.common.config.CentralMapperConfig;
import ua.coffeetamine.common.domain.model.UnorderedPair;
import ua.coffeetamine.ping.domain.model.PingInteraction;
import ua.coffeetamine.ping.domain.model.PingState;
import ua.coffeetamine.ping.domain.model.UserMatch;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface PingMapper {

  Ping toResponse(PingInteraction entity);

  PingListResponse toPingListResponse(Page<PingInteraction> page);

  PingState toState(PingStatus status);

  @Mapping(target = "id", source = "match.id")
  @Mapping(target = "peerUserId", source = "peer.userId")
  @Mapping(target = "peerName", source = "peer.name")
  @Mapping(target = "peerAvatarUrl", source = "peer.avatarUrl")
  @Mapping(target = "initiatingPingId", source = "match.initiatingPingId")
  @Mapping(target = "matchedAt", source = "match.createdAt")
  Match toResponse(MatchView view);

  MatchListResponse toMatchListResponse(Page<MatchView> page);

  @BeanMapping(ignoreByDefault = true)
  @Mapping(target = "fromUserId", source = "fromUserId")
  @Mapping(target = "toUserId", source = "toUserId")
  @Mapping(target = "pairLowId", source = "pair.low")
  @Mapping(target = "pairHighId", source = "pair.high")
  @Mapping(target = "status", constant = "PENDING")
  PingInteraction toNewPing(UUID fromUserId, UUID toUserId, UnorderedPair pair);

  @BeanMapping(ignoreByDefault = true)
  @Mapping(target = "initiatorId", source = "initiatorId")
  @Mapping(target = "recipientId", source = "recipientId")
  @Mapping(target = "initiatingPingId", source = "initiatingPingId")
  UserMatch toNewMatch(UUID initiatorId, UUID recipientId, UUID initiatingPingId);
}
