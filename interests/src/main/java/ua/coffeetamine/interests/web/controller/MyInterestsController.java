package ua.coffeetamine.interests.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import ua.coffeetamine.api.interests.MyInterestsApi;
import ua.coffeetamine.api.interests.dto.UpdateUserInterestsRequest;
import ua.coffeetamine.api.interests.dto.UserInterestsResponse;
import ua.coffeetamine.common.security.RequiresAuthenticated;
import ua.coffeetamine.interests.service.InterestService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequiresAuthenticated
public class MyInterestsController implements MyInterestsApi {

  private final InterestService service;

  @Override
  public ResponseEntity<UserInterestsResponse> getMyInterests() {
    return ResponseEntity.ok(service.getMyInterests());
  }

  @Override
  public ResponseEntity<UserInterestsResponse> replaceMyInterests(
      UpdateUserInterestsRequest request) {
    return ResponseEntity.ok(service.replaceMyInterests(request));
  }
}
