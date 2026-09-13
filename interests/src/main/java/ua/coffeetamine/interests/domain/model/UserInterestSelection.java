package ua.coffeetamine.interests.domain.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import ua.coffeetamine.common.domain.model.BaseAuditableEntity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "user_interest_selections",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_user_interest_selections_user_interest",
          columnNames = {"user_id", "interest_id"}),
      @UniqueConstraint(
          name = "uk_user_interest_selections_user_position",
          columnNames = {"user_id", "selection_order"})
    })
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class UserInterestSelection extends BaseAuditableEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "interest_id", nullable = false)
  private InterestTag interest;

  @Column(name = "selection_order", nullable = false)
  private int selectionOrder;
}
