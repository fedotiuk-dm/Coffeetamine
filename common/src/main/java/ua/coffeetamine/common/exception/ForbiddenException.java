package ua.coffeetamine.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** HTTP 403 — authenticated, but not permitted. */
public class ForbiddenException extends ErrorResponseException {

  public ForbiddenException(ErrorCode errorCode, String message) {
    super(
        HttpStatus.FORBIDDEN,
        ProblemDetails.of(HttpStatus.FORBIDDEN, "Forbidden", message, "forbidden", errorCode),
        null);
  }
}
