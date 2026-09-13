package ua.coffeetamine.moodboard.domain.model;

import java.util.List;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "mood_boards")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@DynamicUpdate
public class MoodBoard extends AppUserKeyedEntity {

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "images", nullable = false, columnDefinition = "jsonb")
  @Builder.Default
  private MoodBoardImages images = new MoodBoardImages(List.of());
}
