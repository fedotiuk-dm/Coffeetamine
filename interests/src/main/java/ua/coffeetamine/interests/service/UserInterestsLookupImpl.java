package ua.coffeetamine.interests.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.common.spi.InterestSummary;
import ua.coffeetamine.common.spi.UserInterestsLookup;
import ua.coffeetamine.interests.domain.model.InterestTag;
import ua.coffeetamine.interests.domain.repository.UserInterestSelectionRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class UserInterestsLookupImpl implements UserInterestsLookup {

  private final UserInterestSelectionRepository repository;

  @Override
  public Set<UUID> interestIdsFor(UUID userId) {
    return repository.findByUserId(userId, UserInterestSelectionRepository.SELECTION_ORDER).stream()
        .map(selection -> selection.getInterest().getId())
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public Map<UUID, Set<UUID>> interestIdsFor(Collection<UUID> userIds) {
    Map<UUID, Set<UUID>> result = new HashMap<>();
    repository
        .findByUserIdIn(userIds)
        .forEach(
            selection ->
                result
                    .computeIfAbsent(selection.getUserId(), _ -> new HashSet<>())
                    .add(selection.getInterest().getId()));
    return result;
  }

  @Override
  public List<InterestSummary> orderedInterestsFor(UUID userId) {
    return repository.findByUserId(userId, UserInterestSelectionRepository.SELECTION_ORDER).stream()
        .map(selection -> toSummary(selection.getInterest()))
        .toList();
  }

  private static InterestSummary toSummary(InterestTag tag) {
    return new InterestSummary(
        tag.getId(), tag.getCode(), tag.getDisplayName(), tag.getCategory(), tag.getSortOrder());
  }
}
