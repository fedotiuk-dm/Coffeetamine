package ua.coffeetamine.moodboard.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import ua.coffeetamine.common.spi.MoodBoardImageInput;
import ua.coffeetamine.common.spi.MoodBoardWriter;

import lombok.RequiredArgsConstructor;

/**
 * Implements the {@code common} {@link MoodBoardWriter} SPI. Delegates to {@link MoodBoardService}
 * so onboarding (in the user module) can compose the write inside its own {@code @Transactional}.
 */
@Component
@RequiredArgsConstructor
class MoodBoardWriterImpl implements MoodBoardWriter {

  private final MoodBoardService service;

  @Override
  public void replaceImages(UUID userId, List<MoodBoardImageInput> images) {
    service.replaceUserMoodBoard(userId, images);
  }
}
