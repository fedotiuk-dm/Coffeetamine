package ua.coffeetamine.common.domain.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import org.hibernate.annotations.UpdateTimestamp;

/**
 * Base for mutable entities. Extends {@link BaseEntity} with:
 *
 * <ul>
 *   <li>{@code updatedAt} — Hibernate-managed timestamp of the last write,
 *   <li>{@code version} — JPA optimistic-locking version. Concurrent updates from two transactions
 *       produce {@link org.springframework.dao.OptimisticLockingFailureException} at flush time on
 *       the loser.
 * </ul>
 *
 * <p>Subclasses must use {@code @SuperBuilder}. Do not combine {@code @CreationTimestamp} /
 * {@code @UpdateTimestamp} with {@code @PrePersist} / {@code @PreUpdate} — they fight each other
 * (see parent {@code CLAUDE.md} → Gotchas).
 */
@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public abstract class BaseAuditableEntity extends BaseEntity {

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;
}
