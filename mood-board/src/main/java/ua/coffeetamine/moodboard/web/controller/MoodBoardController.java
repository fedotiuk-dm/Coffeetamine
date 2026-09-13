package ua.coffeetamine.moodboard.web.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import ua.coffeetamine.api.moodboard.MoodBoardApi;
import ua.coffeetamine.api.moodboard.dto.MoodBoardResponse;
import ua.coffeetamine.api.moodboard.dto.UpdateMoodBoardRequest;
import ua.coffeetamine.common.security.RequiresAuthenticated;
import ua.coffeetamine.moodboard.service.MoodBoardService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequiresAuthenticated
public class MoodBoardController implements MoodBoardApi {

  private final MoodBoardService service;

  @Override
  public ResponseEntity<MoodBoardResponse> getMyMoodBoard() {
    return ResponseEntity.ok(service.getMyMoodBoard());
  }

  @Override
  public ResponseEntity<MoodBoardResponse> getUserMoodBoard(UUID userId) {
    return ResponseEntity.ok(service.getUserMoodBoard(userId));
  }

  @Override
  public ResponseEntity<MoodBoardResponse> replaceMyMoodBoard(UpdateMoodBoardRequest body) {
    return ResponseEntity.ok(service.replaceMyMoodBoard(body));
  }
}
