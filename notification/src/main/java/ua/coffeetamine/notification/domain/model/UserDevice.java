package ua.coffeetamine.notification.domain.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import ua.coffeetamine.common.domain.model.BaseAuditableEntity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "user_devices")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@DynamicUpdate
public class UserDevice extends BaseAuditableEntity {

  @Column(name = "app_user_id", nullable = false, updatable = false)
  private UUID appUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "platform", nullable = false, length = 20)
  private DevicePlatform platform;

  @Column(name = "token", nullable = false, length = 4096)
  private String token;

  @Column(name = "app_version", length = 50)
  private String appVersion;
}
