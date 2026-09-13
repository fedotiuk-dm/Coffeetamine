package ua.coffeetamine.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** HTTP 409 — request collides with current state (duplicate, race, etc.). */
public class ConflictException extends ErrorResponseException {

  public ConflictException(ErrorCode errorCode, String message) {
    super(
        HttpStatus.CONFLICT,
        ProblemDetails.of(HttpStatus.CONFLICT, "Conflict", message, "conflict", errorCode),
        null);
  }
}
