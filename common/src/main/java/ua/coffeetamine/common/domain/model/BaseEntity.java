package ua.coffeetamine.common.domain.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import org.hibernate.annotations.CreationTimestamp;

/**
 * Base for entities with a generated UUID primary key and an immutable creation timestamp. Suitable
 * for append-only records (audit logs, outbox-style events, match events).
 *
 * <p>Mutable entities should extend {@link BaseAuditableEntity} instead — it adds {@code updatedAt}
 * and {@code @Version} for optimistic locking.
 *
 * <p>Subclasses must use {@code @SuperBuilder} — Lombok requires the whole hierarchy to share one
 * builder flavour.
 *
 * <p>The id is assigned by Hibernate at INSERT time. Services and mappers must NOT set it before
 * {@code repository.save()} — that triggers {@code merge()} and breaks {@code @Version == null} on
 * first save under Hibernate 7. For flows that need a known id before persisting (idempotency,
 * externally-correlated events), call {@link jakarta.persistence.EntityManager#persist(Object)}
 * directly with the id pre-set.
 */
@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public abstract class BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
