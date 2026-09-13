package ua.coffeetamine.integration;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.interests.domain.model.InterestTag;
import ua.coffeetamine.interests.domain.model.UserInterestSelection;
import ua.coffeetamine.interests.domain.repository.InterestTagRepository;
import ua.coffeetamine.interests.domain.repository.UserInterestSelectionRepository;
import ua.coffeetamine.moodboard.domain.model.MoodBoard;
import ua.coffeetamine.moodboard.domain.model.MoodBoardImageRef;
import ua.coffeetamine.moodboard.domain.model.MoodBoardImages;
import ua.coffeetamine.moodboard.domain.repository.MoodBoardRepository;
import ua.coffeetamine.presence.domain.model.JitteredLocation;
import ua.coffeetamine.presence.domain.model.PresenceState;
import ua.coffeetamine.presence.domain.model.UserPresence;
import ua.coffeetamine.presence.domain.repository.UserPresenceRepository;
import ua.coffeetamine.user.domain.model.UserProfile;
import ua.coffeetamine.user.domain.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * Programmatic test fixtures. Each helper hits the repository directly and bypasses controllers and
 * the {@code UserOnboardingGuard}, so tests can arrange specific states (an onboarded user with
 * READY presence at known coordinates, for example) without paying the price of going through
 * {@code POST /api/users/me/onboarding} every time.
 *
 * <p>For presence specifically, fixtures write coordinates verbatim with NO jitter — assertions
 * about distance need predictable values. The {@link
 * ua.coffeetamine.presence.service.PresenceServiceImpl#updateMyPresence} jitter behaviour is
 * covered by its own dedicated IT (PresenceJitterIT), not by these seed helpers.
 */
@Component
@RequiredArgsConstructor
public class TestDataFactory {

  private final UserProfileRepository userProfileRepository;
  private final InterestTagRepository interestTagRepository;
  private final UserInterestSelectionRepository selectionRepository;
  private final UserPresenceRepository presenceRepository;
  private final MoodBoardRepository moodBoardRepository;

  /** Onboarded profile — passes the {@code UserOnboardingGuard} check. */
  @Transactional
  public UUID createOnboardedUser(String name) {
    UUID id = UUID.randomUUID();
    userProfileRepository.save(
        UserProfile.builder().userId(id).name(name).onboardingCompleted(true).build());
    return id;
  }

  /** Profile without the onboarding flag — used for testing the guard. */
  @Transactional
  public UUID createIncompleteUser(String name) {
    UUID id = UUID.randomUUID();
    userProfileRepository.save(
        UserProfile.builder().userId(id).name(name).onboardingCompleted(false).build());
    return id;
  }

  @Transactional
  public UUID createInterestTag(String code, String displayName, String category) {
    InterestTag tag =
        InterestTag.builder()
            .code(code)
            .displayName(displayName)
            .category(category)
            .active(true)
            .sortOrder(0)
            .build();
    return interestTagRepository.save(tag).getId();
  }

  @Transactional
  public void selectInterests(UUID userId, List<UUID> tagIds) {
    ArrayList<UserInterestSelection> selections = new ArrayList<>();
    for (int i = 0; i < tagIds.size(); i++) {
      selections.add(
          UserInterestSelection.builder()
              .userId(userId)
              .interest(interestTagRepository.getReferenceById(tagIds.get(i)))
              .selectionOrder(i)
              .build());
    }
    selectionRepository.saveAll(selections);
  }

  /**
   * Writes a READY presence row directly with the given coordinates. <em>No jitter applied</em> —
   * fixtures need predictable coords for distance assertions.
   */
  @Transactional
  public void seedReadyPresence(UUID userId, double latitude, double longitude) {
    presenceRepository.save(
        UserPresence.builder()
            .userId(userId)
            .status(PresenceState.READY)
            .location(new JitteredLocation(latitude, longitude))
            .build());
  }

  @Transactional
  public void seedNotReadyPresence(UUID userId) {
    presenceRepository.save(
        UserPresence.builder().userId(userId).status(PresenceState.NOT_READY).build());
  }

  /** Seeds a 6-image mood board with Unsplash-flavoured stub URLs. */
  @Transactional
  public void seedSixImageMoodBoard(UUID userId) {
    List<MoodBoardImageRef> images =
        IntStream.range(0, 6)
            .mapToObj(
                i ->
                    new MoodBoardImageRef(
                        "stub-photo-" + i,
                        URI.create("https://images.unsplash.com/photo-" + i),
                        "Stub Photographer " + i,
                        URI.create("https://unsplash.com/@stub" + i),
                        URI.create("https://unsplash.com/photos/stub-photo-" + i)))
            .toList();
    MoodBoard board = MoodBoard.builder().userId(userId).build();
    board.setImages(new MoodBoardImages(images));
    moodBoardRepository.save(board);
  }
}
