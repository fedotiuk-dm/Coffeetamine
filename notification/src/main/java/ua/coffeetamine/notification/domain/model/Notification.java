package ua.coffeetamine.notification.domain.model;

import java.time.Instant;
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

/**
 * In-app notification row. Peer fields are denormalised at insert time so list views need no joins
 * and remain stable if the peer later renames or removes their avatar. {@code pingId} / {@code
 * matchId} disambiguate the reference based on {@code type}.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@DynamicUpdate
public class Notification extends BaseAuditableEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, updatable = false, length = 30)
  private NotificationType type;

  @Column(name = "peer_user_id", nullable = false, updatable = false)
  private UUID peerUserId;

  @Column(name = "peer_name", nullable = false, updatable = false, length = 50)
  private String peerName;

  @Column(name = "peer_avatar_url", updatable = false, length = 500)
  private String peerAvatarUrl;

  @Column(name = "ping_id", updatable = false)
  private UUID pingId;

  @Column(name = "match_id", updatable = false)
  private UUID matchId;

  @Column(name = "is_read", nullable = false)
  @Builder.Default
  private boolean read = false;

  @Column(name = "read_at")
  private Instant readAt;
}
