package ua.coffeetamine.moodboard.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.common.spi.MoodBoardImageSummary;
import ua.coffeetamine.common.spi.MoodBoardLookup;
import ua.coffeetamine.common.spi.MoodBoardSummary;
import ua.coffeetamine.moodboard.domain.model.MoodBoard;
import ua.coffeetamine.moodboard.domain.model.MoodBoardImageRef;
import ua.coffeetamine.moodboard.domain.repository.MoodBoardRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class MoodBoardLookupImpl implements MoodBoardLookup {

  private final MoodBoardRepository repository;

  @Override
  public Optional<MoodBoardSummary> findFor(UUID userId) {
    return repository.findById(userId).map(MoodBoardLookupImpl::toSummary);
  }

  private static MoodBoardSummary toSummary(MoodBoard board) {
    return new MoodBoardSummary(
        board.getUserId(),
        board.getImages().images().stream().map(MoodBoardLookupImpl::toImageSummary).toList(),
        board.getUpdatedAt());
  }

  private static MoodBoardImageSummary toImageSummary(MoodBoardImageRef ref) {
    return new MoodBoardImageSummary(
        ref.unsplashPhotoId(),
        ref.imageUrl(),
        ref.photographerName(),
        ref.photographerProfileUrl(),
        ref.attributionUrl());
  }
}
