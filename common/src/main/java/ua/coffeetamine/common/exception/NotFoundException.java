package ua.coffeetamine.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** HTTP 404 — requested resource doesn't exist. */
public class NotFoundException extends ErrorResponseException {

  public NotFoundException(ErrorCode errorCode, String message) {
    super(HttpStatus.NOT_FOUND, asProblemDetail(message, errorCode), null);
  }

  public NotFoundException(String entityType, Object entityId) {
    super(
        HttpStatus.NOT_FOUND,
        asProblemDetail(
            String.format("%s with id '%s' not found", entityType, entityId),
            ErrorCode.NOT_FOUND_ENTITY),
        null);
  }

  private static ProblemDetail asProblemDetail(String message, ErrorCode errorCode) {
    return ProblemDetails.of(
        HttpStatus.NOT_FOUND, "Resource Not Found", message, "not-found", errorCode);
  }
}
