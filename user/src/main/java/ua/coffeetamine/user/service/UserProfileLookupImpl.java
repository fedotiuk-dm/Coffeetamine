package ua.coffeetamine.user.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.common.spi.UserProfileLookup;
import ua.coffeetamine.common.spi.UserProfileSummary;
import ua.coffeetamine.user.domain.model.UserProfile;
import ua.coffeetamine.user.domain.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class UserProfileLookupImpl implements UserProfileLookup {

  private final UserProfileRepository repository;

  @Override
  public Optional<UserProfileSummary> findById(UUID userId) {
    return repository.findById(userId).map(UserProfileLookupImpl::toSummary);
  }

  @Override
  public Map<UUID, UserProfileSummary> findAllByIds(Collection<UUID> userIds) {
    return repository.findAllById(userIds).stream()
        .collect(Collectors.toMap(UserProfile::getUserId, UserProfileLookupImpl::toSummary));
  }

  private static UserProfileSummary toSummary(UserProfile profile) {
    return new UserProfileSummary(
        profile.getUserId(), profile.getName(), profile.getAvatarUrl(), profile.getAbout());
  }
}
