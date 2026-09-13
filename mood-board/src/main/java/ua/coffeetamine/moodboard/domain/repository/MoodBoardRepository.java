package ua.coffeetamine.moodboard.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.moodboard.domain.model.MoodBoard;

@Repository
@Transactional(readOnly = true)
public interface MoodBoardRepository extends JpaRepository<MoodBoard, UUID> {

  /**
   * Returns the user's mood board, creating an empty one on first access. Idempotent: the unique PK
   * constraint on {@code user_id} prevents duplicates under concurrent first-access.
   */
  default MoodBoard findOrCreate(UUID userId) {
    return findById(userId).orElseGet(() -> save(MoodBoard.builder().userId(userId).build()));
  }
}
