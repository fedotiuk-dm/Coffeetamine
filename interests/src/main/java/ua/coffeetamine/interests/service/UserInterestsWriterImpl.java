package ua.coffeetamine.interests.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import ua.coffeetamine.common.spi.UserInterestsWriter;

import lombok.RequiredArgsConstructor;

/**
 * Implements the {@code common} {@link UserInterestsWriter} SPI. Delegates to {@link
 * InterestService} so onboarding (in the user module) can compose the call inside its own
 * {@code @Transactional}.
 */
@Component
@RequiredArgsConstructor
class UserInterestsWriterImpl implements UserInterestsWriter {

  private final InterestService service;

  @Override
  public void replaceSelections(UUID userId, List<UUID> interestIds) {
    service.replaceUserInterests(userId, interestIds);
  }
}
