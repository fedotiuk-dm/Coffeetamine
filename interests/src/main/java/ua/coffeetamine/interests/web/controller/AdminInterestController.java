package ua.coffeetamine.interests.web.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import ua.coffeetamine.api.interests.AdminInterestsApi;
import ua.coffeetamine.api.interests.dto.CreateInterestRequest;
import ua.coffeetamine.api.interests.dto.Interest;
import ua.coffeetamine.api.interests.dto.UpdateInterestRequest;
import ua.coffeetamine.common.security.RequiresAdmin;
import ua.coffeetamine.interests.service.InterestService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequiresAdmin
public class AdminInterestController implements AdminInterestsApi {

  private final InterestService service;

  @Override
  public ResponseEntity<Interest> createInterest(CreateInterestRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.createInterest(request));
  }

  @Override
  public ResponseEntity<Interest> updateInterest(UUID id, UpdateInterestRequest request) {
    return ResponseEntity.ok(service.updateInterest(id, request));
  }

  @Override
  public ResponseEntity<Void> deleteInterest(UUID id) {
    service.deactivateInterest(id);
    return ResponseEntity.noContent().build();
  }
}
