package ua.coffeetamine.interests.web.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import ua.coffeetamine.api.interests.InterestsApi;
import ua.coffeetamine.api.interests.dto.Interest;
import ua.coffeetamine.api.interests.dto.InterestListResponse;
import ua.coffeetamine.api.interests.dto.UserInterestsResponse;
import ua.coffeetamine.common.security.RequiresAuthenticated;
import ua.coffeetamine.interests.service.InterestService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequiresAuthenticated
public class InterestCatalogController implements InterestsApi {

  private final InterestService service;

  @Override
  public ResponseEntity<InterestListResponse> listInterests(
      String search, String category, Boolean activeOnly, Pageable pageable) {
    return ResponseEntity.ok(service.listInterests(search, category, activeOnly, pageable));
  }

  @Override
  public ResponseEntity<Interest> getInterest(UUID id) {
    return ResponseEntity.ok(service.getInterest(id));
  }

  @Override
  public ResponseEntity<UserInterestsResponse> getUserInterests(UUID userId) {
    return ResponseEntity.ok(service.getUserInterests(userId));
  }
}
