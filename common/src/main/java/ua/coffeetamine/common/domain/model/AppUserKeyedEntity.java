package ua.coffeetamine.common.domain.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Base for entities whose primary key IS the internal app user UUID. The id is assigned by the
 * service from {@code SecurityUtils.getCurrentUserId()} — which now returns the resolved internal
 * {@code app_user_id} rather than the raw JWT subject. No DB-level FK to a {@code users} table —
 * external identity lives in the OIDC provider and is mapped via {@code external_identities}.
 *
 * <p>Adds audit + optimistic-locking semantics: {@code createdAt}, {@code updatedAt},
 * {@code @Version}.
 *
 * <p>Subclasses must use {@code @SuperBuilder}.
 */
@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public abstract class AppUserKeyedEntity {

  @Id
  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;
}
