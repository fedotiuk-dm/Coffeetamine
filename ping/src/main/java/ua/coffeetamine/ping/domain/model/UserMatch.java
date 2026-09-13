package ua.coffeetamine.ping.domain.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import ua.coffeetamine.common.domain.model.BaseAuditableEntity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "user_matches")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class UserMatch extends BaseAuditableEntity {

  @Column(name = "initiator_id", nullable = false, updatable = false)
  private UUID initiatorId;

  @Column(name = "recipient_id", nullable = false, updatable = false)
  private UUID recipientId;

  @Column(name = "initiating_ping_id", nullable = false, updatable = false)
  private UUID initiatingPingId;

  /** Returns the participant id that is not {@code userId}. Caller must ensure user is involved. */
  public UUID peerOf(UUID userId) {
    return initiatorId.equals(userId) ? recipientId : initiatorId;
  }

  public boolean involves(UUID userId) {
    return initiatorId.equals(userId) || recipientId.equals(userId);
  }
}
