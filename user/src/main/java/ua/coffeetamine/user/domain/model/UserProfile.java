package ua.coffeetamine.user.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import ua.coffeetamine.common.domain.model.AppUserKeyedEntity;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import org.hibernate.annotations.DynamicUpdate;

/**
 * User profile keyed by the internal app user UUID (from the JWT {@code sub} claim). Email is NOT
 * stored — it is read from the JWT at response-build time for owner views only, never exposed in
 * public views.
 */
@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@DynamicUpdate
public class UserProfile extends AppUserKeyedEntity {

  @Column(name = "name", nullable = false, length = 50)
  private String name;

  @Column(name = "avatar_url", length = 500)
  private String avatarUrl;

  @Column(name = "about", length = 500)
  private String about;

  @Column(name = "onboarding_completed", nullable = false)
  @Builder.Default
  private boolean onboardingCompleted = false;
}
