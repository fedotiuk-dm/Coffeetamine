package ua.coffeetamine.interests.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import ua.coffeetamine.common.domain.model.BaseAuditableEntity;
import ua.coffeetamine.common.domain.model.Sortable;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(
    name = "interest_tags",
    uniqueConstraints = @UniqueConstraint(name = "uk_interest_tags_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@DynamicUpdate
public class InterestTag extends BaseAuditableEntity implements Sortable {

  @Column(name = "code", nullable = false, length = 50, updatable = false)
  private String code;

  @Column(name = "display_name", nullable = false, length = 80)
  private String displayName;

  @Column(name = "category", nullable = false, length = 50)
  private String category;

  @Column(name = "active", nullable = false)
  @Builder.Default
  private boolean active = true;

  @Column(name = "sort_order", nullable = false)
  @Builder.Default
  private int sortOrder = 0;
}
