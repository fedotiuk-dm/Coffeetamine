package ua.coffeetamine.interests.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.api.interests.dto.CreateInterestRequest;
import ua.coffeetamine.api.interests.dto.Interest;
import ua.coffeetamine.api.interests.dto.InterestListResponse;
import ua.coffeetamine.api.interests.dto.UpdateInterestRequest;
import ua.coffeetamine.api.interests.dto.UpdateUserInterestsRequest;
import ua.coffeetamine.api.interests.dto.UserInterestsResponse;
import ua.coffeetamine.common.exception.BadRequestException;
import ua.coffeetamine.common.exception.ConflictException;
import ua.coffeetamine.common.exception.ErrorCode;
import ua.coffeetamine.common.exception.NotFoundException;
import ua.coffeetamine.common.security.SecurityUtils;
import ua.coffeetamine.common.security.UserOnboardingGuard;
import ua.coffeetamine.interests.domain.model.InterestTag;
import ua.coffeetamine.interests.domain.model.UserInterestSelection;
import ua.coffeetamine.interests.domain.repository.InterestTagRepository;
import ua.coffeetamine.interests.domain.repository.UserInterestSelectionRepository;
import ua.coffeetamine.interests.mapper.InterestTagMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterestServiceImpl implements InterestService {

  private final InterestTagRepository tagRepository;
  private final UserInterestSelectionRepository selectionRepository;
  private final InterestTagMapper mapper;
  private final UserOnboardingGuard onboardingGuard;
  private final SecurityUtils securityUtils;

  @Override
  public InterestListResponse listInterests(
      String search, String category, Boolean activeOnly, Pageable pageable) {
    return mapper.toListResponse(tagRepository.findCatalog(search, category, activeOnly, pageable));
  }

  @Override
  public Interest getInterest(UUID id) {
    return mapper.toResponse(requireActiveTag(id));
  }

  @Override
  @Transactional
  public Interest createInterest(CreateInterestRequest request) {
    if (tagRepository.existsByCode(request.getCode())) {
      throw new ConflictException(
          ErrorCode.CONFLICT_DUPLICATE, "Interest tag code already exists: " + request.getCode());
    }
    InterestTag tag = mapper.toEntity(request);
    if (request.getSortOrder() == null) {
      tag.setSortOrder(tagRepository.getNextSortOrder());
    }
    return mapper.toResponse(tagRepository.save(tag));
  }

  @Override
  @Transactional
  public Interest updateInterest(UUID id, UpdateInterestRequest request) {
    InterestTag tag = requireTag(id);
    mapper.updateFromRequest(request, tag);
    return mapper.toResponse(tag);
  }

  @Override
  @Transactional
  public void deactivateInterest(UUID id) {
    requireTag(id).setActive(false);
  }

  @Override
  public UserInterestsResponse getUserInterests(UUID userId) {
    return mapper.toUserInterestsResponse(
        userId, selectionRepository.findInterestsByUserId(userId));
  }

  @Override
  public UserInterestsResponse getMyInterests() {
    return getUserInterests(securityUtils.getCurrentUserId());
  }

  @Override
  @Transactional
  public UserInterestsResponse replaceMyInterests(UpdateUserInterestsRequest request) {
    UUID userId = securityUtils.getCurrentUserId();
    onboardingGuard.requireOnboardingComplete(userId);
    Map<UUID, InterestTag> tagsById = doReplace(userId, request.getInterestIds());
    return mapper.toUserInterestsResponse(
        userId, request.getInterestIds().stream().map(tagsById::get).toList());
  }

  @Override
  @Transactional
  public void replaceUserInterests(UUID userId, List<UUID> interestIds) {
    doReplace(userId, interestIds);
  }

  private Map<UUID, InterestTag> doReplace(UUID userId, List<UUID> interestIds) {
    if (Set.copyOf(interestIds).size() < interestIds.size()) {
      throw new BadRequestException(
          ErrorCode.VALIDATION_BAD_REQUEST, "Interest selection cannot contain duplicate tags");
    }

    Map<UUID, InterestTag> tagsById =
        tagRepository.findAllActiveByIds(interestIds).stream()
            .collect(Collectors.toMap(InterestTag::getId, Function.identity()));

    List<UserInterestSelection> selections =
        IntStream.range(0, interestIds.size())
            .mapToObj(i -> mapper.toSelection(userId, requireTag(tagsById, interestIds.get(i)), i))
            .toList();

    selectionRepository.deleteByUserId(userId);
    selectionRepository.flush();
    selectionRepository.saveAll(selections);
    return tagsById;
  }

  private InterestTag requireTag(UUID id) {
    return tagRepository.findById(id).orElseThrow(() -> new NotFoundException("InterestTag", id));
  }

  private InterestTag requireActiveTag(UUID id) {
    return tagRepository
        .findActiveById(id)
        .orElseThrow(
            () ->
                new NotFoundException(
                    ErrorCode.INTERESTS_TAG_UNKNOWN, "Interest tag is unknown or inactive: " + id));
  }

  private static InterestTag requireTag(Map<UUID, InterestTag> tagsById, UUID id) {
    InterestTag tag = tagsById.get(id);
    if (tag == null) {
      throw new NotFoundException(
          ErrorCode.INTERESTS_TAG_UNKNOWN, "Interest tag is unknown or inactive: " + id);
    }
    return tag;
  }
}
