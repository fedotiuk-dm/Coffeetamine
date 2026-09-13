package ua.coffeetamine.common.exception;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

/**
 * Translates framework + domain exceptions into RFC 7807 {@link ProblemDetail} responses.
 *
 * <p>Every response carries an {@code errorCode} property and, when available, a {@code traceId}
 * and {@code instance}. The order of handlers matters: more specific exception types come first so
 * they aren't swallowed by {@link #handleGeneral}.
 *
 * <p>{@link ErrorResponseException} subclasses ({@link NotFoundException}, {@link
 * BadRequestException}, …) carry their own pre-built ProblemDetail and only need trace-id
 * enrichment.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  private static final String ERRORS_KEY = "errors";
  private static final AuthenticationTrustResolver TRUST_RESOLVER =
      new AuthenticationTrustResolverImpl();

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
    List<Map<String, String>> errors =
        Stream.concat(
                ex.getBindingResult().getFieldErrors().stream()
                    .map(e -> validationError(e.getField(), e)),
                ex.getBindingResult().getGlobalErrors().stream()
                    .map(GlobalExceptionHandler::objectError))
            .toList();
    log.warn("Validation failed: {}", errors);

    ProblemDetail problem =
        problem(
            HttpStatus.BAD_REQUEST,
            "Validation Error",
            "Validation failed",
            "validation",
            ErrorCode.VALIDATION_FIELD_INVALID);
    problem.setProperty(ERRORS_KEY, errors);
    return problem;
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ProblemDetail handleHandlerMethodValidation(HandlerMethodValidationException ex) {
    List<Map<String, String>> errors =
        ex.getParameterValidationResults().stream()
            .flatMap(GlobalExceptionHandler::parameterValidationErrors)
            .toList();
    log.warn("Parameter validation failed: {}", errors);

    ProblemDetail problem =
        problem(
            HttpStatus.BAD_REQUEST,
            "Validation Error",
            "Parameter validation failed",
            "validation",
            ErrorCode.VALIDATION_FIELD_INVALID);
    problem.setProperty(
        ERRORS_KEY,
        errors.isEmpty()
            ? List.of(
                validationError("parameters", "Parameter validation failed", "ValidationError"))
            : errors);
    return problem;
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    String message =
        String.format("Invalid value '%s' for parameter '%s'", ex.getValue(), ex.getName());
    log.warn("Type mismatch: {}", message);
    return problem(
        HttpStatus.BAD_REQUEST,
        "Type Mismatch",
        message,
        "type-mismatch",
        ErrorCode.VALIDATION_TYPE_MISMATCH);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ProblemDetail handleMissingParam(MissingServletRequestParameterException ex) {
    String message = String.format("Required parameter '%s' is missing", ex.getParameterName());
    log.warn("Missing parameter: {}", message);
    return problem(
        HttpStatus.BAD_REQUEST,
        "Missing Parameter",
        message,
        "missing-parameter",
        ErrorCode.VALIDATION_BAD_REQUEST);
  }

  @ExceptionHandler({
    HttpRequestMethodNotSupportedException.class,
    HttpMediaTypeNotSupportedException.class,
    HttpMediaTypeNotAcceptableException.class
  })
  public ProblemDetail handleStandardMvc(ErrorResponse ex) {
    HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
    log.warn("{}: {}", status.value(), ex.getBody().getDetail());
    return problem(
        status,
        status.getReasonPhrase(),
        ex.getBody().getDetail(),
        slug(status),
        fallbackErrorCode(status.value()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException ex) {
    log.warn("Invalid JSON: {}", ex.getMessage());
    return problem(
        HttpStatus.BAD_REQUEST,
        "Invalid Request Body",
        "Malformed or unreadable JSON in request body",
        "invalid-json",
        ErrorCode.VALIDATION_JSON_PARSE);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
    log.warn("Invalid argument: {}", ex.getMessage());
    return problem(
        HttpStatus.BAD_REQUEST,
        "Invalid Argument",
        ProblemDetails.hasText(ex.getMessage()) ? ex.getMessage() : "Invalid request",
        "invalid-argument",
        ErrorCode.VALIDATION_BAD_REQUEST);
  }

  // SecurityConfig normally short-circuits these via ProblemDetailSecurityHandler.
  // These handlers exist as a defence-in-depth fallback for exceptions thrown after
  // the security filter chain has admitted the request (e.g. @PreAuthorize denials).
  @ExceptionHandler(AuthenticationException.class)
  public ProblemDetail handleAuthentication(AuthenticationException ex) {
    log.warn("Authentication failed: {}", ex.getMessage());
    return problem(
        HttpStatus.UNAUTHORIZED,
        "Authentication Failed",
        "Authentication failed",
        "unauthorized",
        ErrorCode.AUTH_LOGIN_FAILED);
  }

  @ExceptionHandler(AuthorizationDeniedException.class)
  public ProblemDetail handleAuthorization(AuthorizationDeniedException ex) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || TRUST_RESOLVER.isAnonymous(authentication)) {
      log.debug("Authentication required: {}", ex.getMessage());
      return problem(
          HttpStatus.UNAUTHORIZED,
          "Authentication Required",
          "Authentication required",
          "unauthorized",
          ErrorCode.AUTH_UNAUTHORIZED);
    }
    log.warn("Authorization denied: {}", ex.getMessage());
    return problem(
        HttpStatus.FORBIDDEN,
        "Access Denied",
        "Access denied",
        "forbidden",
        ErrorCode.AUTH_ACCESS_DENIED);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ProblemDetail handleNoResource(NoResourceFoundException ex) {
    String path = ex.getResourcePath();
    if (path.endsWith("favicon.ico")) {
      return ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    }
    log.debug("Resource not found: {}", path);
    return problem(
        HttpStatus.NOT_FOUND,
        "Not Found",
        String.format("Resource '%s' not found", path),
        "not-found",
        ErrorCode.NOT_FOUND_RESOURCE);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
    ex.getMostSpecificCause();
    String rootMessage =
        ex.getMostSpecificCause().getMessage() != null
            ? ex.getMostSpecificCause().getMessage()
            : ex.getMessage();
    log.warn("Data integrity violation: {}", rootMessage);
    return problem(
        HttpStatus.CONFLICT,
        "Data Integrity Violation",
        "Data integrity constraint violation",
        "data-integrity",
        ErrorCode.CONFLICT_DATA_INTEGRITY);
  }

  @ExceptionHandler(ErrorResponseException.class)
  public ProblemDetail handleErrorResponse(ErrorResponseException ex) {
    ProblemDetail problem = ex.getBody();
    ensureErrorCode(problem, fallbackErrorCode(problem.getStatus()));
    log.warn("{}: {}", ex.getStatusCode().value(), problem.getDetail());
    return withTraceId(problem);
  }

  @ExceptionHandler(IllegalStateException.class)
  public ProblemDetail handleIllegalState(IllegalStateException ex) {
    log.error("Illegal state", ex);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Illegal State",
        "Internal server error",
        "illegal-state",
        ErrorCode.INTERNAL_UNEXPECTED);
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleGeneral(Exception ex) {
    log.error("Unexpected error", ex);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Internal Server Error",
        "Internal server error",
        "internal",
        ErrorCode.INTERNAL_SERVER_ERROR);
  }

  private static ProblemDetail problem(
      HttpStatus status, String title, String detail, String typeSlug, ErrorCode errorCode) {
    return withTraceId(ProblemDetails.of(status, title, detail, typeSlug, errorCode));
  }

  private static ProblemDetail withTraceId(ProblemDetail problem) {
    ProblemDetails.addTraceId(problem);
    ProblemDetails.addCurrentRequestInstance(problem);
    return problem;
  }

  private static void ensureErrorCode(ProblemDetail problem, ErrorCode fallback) {
    Map<String, Object> properties = problem.getProperties();
    if (properties == null || !properties.containsKey(ProblemDetails.ERROR_CODE_KEY)) {
      problem.setProperty(ProblemDetails.ERROR_CODE_KEY, fallback.getCode());
    }
  }

  private static ErrorCode fallbackErrorCode(int status) {
    return switch (status) {
      case 400 -> ErrorCode.VALIDATION_BAD_REQUEST;
      case 401 -> ErrorCode.AUTH_UNAUTHORIZED;
      case 403 -> ErrorCode.AUTH_ACCESS_DENIED;
      case 404 -> ErrorCode.NOT_FOUND_RESOURCE;
      case 409 -> ErrorCode.CONFLICT_DUPLICATE;
      default ->
          status >= 400 && status < 500
              ? ErrorCode.VALIDATION_BAD_REQUEST
              : ErrorCode.INTERNAL_SERVER_ERROR;
    };
  }

  private static String slug(HttpStatus status) {
    return status.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  private static Stream<Map<String, String>> parameterValidationErrors(
      ParameterValidationResult result) {
    String name = result.getMethodParameter().getParameterName();
    String paramName =
        ProblemDetails.hasText(name)
            ? name
            : "arg" + result.getMethodParameter().getParameterIndex();
    return result.getResolvableErrors().stream().map(error -> validationError(paramName, error));
  }

  private static Map<String, String> objectError(ObjectError error) {
    return validationError(error.getObjectName(), error);
  }

  private static Map<String, String> validationError(String field, MessageSourceResolvable error) {
    return validationError(field, message(error), code(error));
  }

  private static Map<String, String> validationError(String field, String message, String code) {
    return Map.of("field", field, "message", message, "code", code);
  }

  private static String message(MessageSourceResolvable error) {
    String message = error.getDefaultMessage();
    if (ProblemDetails.hasText(message)) {
      return message;
    }
    return lastCode(error, "Invalid value");
  }

  private static String code(MessageSourceResolvable error) {
    return lastCode(error, "ValidationError");
  }

  private static String lastCode(MessageSourceResolvable error, String fallback) {
    String[] codes = error.getCodes();
    if (codes != null) {
      for (int i = codes.length - 1; i >= 0; i--) {
        if (ProblemDetails.hasText(codes[i])) {
          return codes[i];
        }
      }
    }
    return fallback;
  }
}
