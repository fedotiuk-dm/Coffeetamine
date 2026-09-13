package ua.coffeetamine.ping.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import ua.coffeetamine.common.domain.repository.GenericSpecification;
import ua.coffeetamine.ping.domain.model.PingInteraction;
import ua.coffeetamine.ping.domain.model.PingInteraction_;
import ua.coffeetamine.ping.domain.model.PingState;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PingInteractionSpec {

  public static Specification<PingInteraction> sent(UUID fromUserId, PingState status) {
    return GenericSpecification.<PingInteraction>spec()
        .and(GenericSpecification.eq(PingInteraction_.FROM_USER_ID, fromUserId))
        .and(status, s -> GenericSpecification.eq(PingInteraction_.STATUS, s))
        .build();
  }

  public static Specification<PingInteraction> received(UUID toUserId, PingState status) {
    return GenericSpecification.<PingInteraction>spec()
        .and(GenericSpecification.eq(PingInteraction_.TO_USER_ID, toUserId))
        .and(status, s -> GenericSpecification.eq(PingInteraction_.STATUS, s))
        .build();
  }
}
