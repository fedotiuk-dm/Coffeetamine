package ua.coffeetamine.presence.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import ua.coffeetamine.common.domain.model.AppUserKeyedEntity;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "user_presences")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@DynamicInsert
@DynamicUpdate
public class UserPresence extends AppUserKeyedEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private PresenceState status = PresenceState.NOT_READY;

  @Column(name = "mood", length = 200)
  private String mood;

  @Embedded private JitteredLocation location;
}
