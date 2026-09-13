package ua.coffeetamine.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** HTTP 400 — request violates a domain rule. */
public class BadRequestException extends ErrorResponseException {

  public BadRequestException(ErrorCode errorCode, String message) {
    super(
        HttpStatus.BAD_REQUEST,
        ProblemDetails.of(HttpStatus.BAD_REQUEST, "Bad Request", message, "bad-request", errorCode),
        null);
  }
}
