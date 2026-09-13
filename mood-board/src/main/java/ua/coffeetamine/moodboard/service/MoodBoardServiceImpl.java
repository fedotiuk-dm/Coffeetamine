package ua.coffeetamine.moodboard.service;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.api.moodboard.dto.MoodBoardResponse;
import ua.coffeetamine.api.moodboard.dto.UpdateMoodBoardRequest;
import ua.coffeetamine.common.exception.BadRequestException;
import ua.coffeetamine.common.exception.ErrorCode;
import ua.coffeetamine.common.exception.NotFoundException;
import ua.coffeetamine.common.security.SecurityUtils;
import ua.coffeetamine.common.security.UserOnboardingGuard;
import ua.coffeetamine.common.spi.MoodBoardImageInput;
import ua.coffeetamine.moodboard.domain.model.MoodBoard;
import ua.coffeetamine.moodboard.domain.model.MoodBoardImageRef;
import ua.coffeetamine.moodboard.domain.model.MoodBoardImages;
import ua.coffeetamine.moodboard.domain.repository.MoodBoardRepository;
import ua.coffeetamine.moodboard.mapper.MoodBoardMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MoodBoardServiceImpl implements MoodBoardService {

  private static final String UNSPLASH_HOST_SUFFIX = "unsplash.com";
  private static final int REQUIRED_IMAGE_COUNT = 6;

  private final MoodBoardRepository repository;
  private final MoodBoardMapper mapper;
  private final UserOnboardingGuard onboardingGuard;
  private final SecurityUtils securityUtils;

  @Override
  @Transactional
  public MoodBoardResponse getMyMoodBoard() {
    return mapper.toResponse(repository.findOrCreate(securityUtils.getCurrentUserId()));
  }

  @Override
  public MoodBoardResponse getUserMoodBoard(UUID userId) {
    return mapper.toResponse(
        repository.findById(userId).orElseThrow(() -> new NotFoundException("MoodBoard", userId)));
  }

  @Override
  @Transactional
  public MoodBoardResponse replaceMyMoodBoard(UpdateMoodBoardRequest request) {
    UUID callerId = securityUtils.getCurrentUserId();
    onboardingGuard.requireOnboardingComplete(callerId);
    request
        .getImages()
        .forEach(
            img ->
                requireUnsplashUris(
                    img.getImageUrl(), img.getAttributionUrl(), img.getPhotographerProfileUrl()));
    MoodBoard board = repository.findOrCreate(callerId);
    board.setImages(new MoodBoardImages(mapper.toRefs(request.getImages())));
    return mapper.toResponse(board);
  }

  @Override
  @Transactional
  public void replaceUserMoodBoard(UUID userId, List<MoodBoardImageInput> images) {
    if (images.size() != REQUIRED_IMAGE_COUNT) {
      throw new BadRequestException(
          ErrorCode.MOOD_BOARD_INVALID_SIZE, "Mood board must contain exactly 6 images");
    }
    images.forEach(
        img ->
            requireUnsplashUris(
                img.imageUrl(), img.attributionUrl(), img.photographerProfileUrl()));
    MoodBoard board = repository.findOrCreate(userId);
    board.setImages(new MoodBoardImages(images.stream().map(MoodBoardServiceImpl::toRef).toList()));
  }

  private static MoodBoardImageRef toRef(MoodBoardImageInput input) {
    return new MoodBoardImageRef(
        input.unsplashPhotoId(),
        input.imageUrl(),
        input.photographerName(),
        input.photographerProfileUrl(),
        input.attributionUrl());
  }

  private static void requireUnsplashUris(
      URI imageUrl, URI attributionUrl, URI photographerProfileUrl) {
    if (notUnsplash(imageUrl)
        || notUnsplash(attributionUrl)
        || (photographerProfileUrl != null && notUnsplash(photographerProfileUrl))) {
      throw new BadRequestException(
          ErrorCode.MOOD_BOARD_UNSPLASH_REQUIRED,
          "Mood board images must use Unsplash URLs and attribution");
    }
  }

  private static boolean notUnsplash(URI uri) {
    if (uri == null) {
      return true;
    }
    String host = uri.getHost();
    if (host == null) {
      return true;
    }
    host = host.toLowerCase(Locale.ROOT);
    return !host.equals(UNSPLASH_HOST_SUFFIX) && !host.endsWith("." + UNSPLASH_HOST_SUFFIX);
  }
}
