package ua.coffeetamine.common.spi;

import java.util.List;
import java.util.UUID;

/**
 * Cross-module write port for mood boards. Used by {@code user} during onboarding to atomically
 * seed the user's 6 Unsplash references inside the onboarding transaction. Unsplash-host validation
 * runs in the implementation so callers do not need to know the rule.
 */
public interface MoodBoardWriter {

  /**
   * Atomically replace the user's mood board with the supplied images. MVP rule: exactly 6 entries;
   * each URL must be hosted on {@code unsplash.com}. The caller wraps this in its own
   * {@code @Transactional} for cross-module atomicity.
   *
   * <p>Throws {@code BadRequestException(MOOD_BOARD_UNSPLASH_REQUIRED)} for non-Unsplash refs,
   * {@code BadRequestException(MOOD_BOARD_INVALID_SIZE)} when count != 6.
   */
  void replaceImages(UUID userId, List<MoodBoardImageInput> images);
}
