package ua.coffeetamine.moodboard.service;

import java.util.List;
import java.util.UUID;

import ua.coffeetamine.api.moodboard.dto.MoodBoardResponse;
import ua.coffeetamine.api.moodboard.dto.UpdateMoodBoardRequest;
import ua.coffeetamine.common.spi.MoodBoardImageInput;

public interface MoodBoardService {

  MoodBoardResponse getMyMoodBoard();

  MoodBoardResponse getUserMoodBoard(UUID userId);

  MoodBoardResponse replaceMyMoodBoard(UpdateMoodBoardRequest request);

  /**
   * System-level replace by explicit user ID using the cross-module image record. Used by the
   * {@code MoodBoardWriter} port during onboarding so the write joins the caller's
   * {@code @Transactional}. Same validation rules as {@link #replaceMyMoodBoard}.
   */
  void replaceUserMoodBoard(UUID userId, List<MoodBoardImageInput> images);
}
