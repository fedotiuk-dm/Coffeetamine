package ua.coffeetamine.ping.domain.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import ua.coffeetamine.common.domain.model.BaseAuditableEntity;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "pings")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@DynamicUpdate
public class PingInteraction extends BaseAuditableEntity {

  @Column(name = "from_user_id", nullable = false, updatable = false)
  private UUID fromUserId;

  @Column(name = "to_user_id", nullable = false, updatable = false)
  private UUID toUserId;

  @Column(name = "pair_low_id", nullable = false, updatable = false)
  private UUID pairLowId;

  @Column(name = "pair_high_id", nullable = false, updatable = false)
  private UUID pairHighId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private PingState status = PingState.PENDING;
}
