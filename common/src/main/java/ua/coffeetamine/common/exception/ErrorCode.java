package ua.coffeetamine.common.exception;

import org.springframework.http.ProblemDetail;

import lombok.Getter;

/**
 * Standardised, machine-readable error codes. Pattern: {@code DOMAIN_NUMERIC} — {@code AUTH_001},
 * {@code VALIDATION_004}, {@code USER_002}.
 *
 * <p>Codes are stable: clients (mobile, dashboards) may dispatch on them, so renaming or
 * renumbering is a breaking change. The {@code defaultMessage} is the fallback that ships inside
 * the {@link ProblemDetail} when a caller doesn't supply one.
 *
 * <p>Add domain-specific codes here as feature modules land — keep them grouped by domain prefix.
 */
@Getter
public enum ErrorCode {
  AUTH_LOGIN_FAILED("AUTH_001", "Authentication failed"),
  AUTH_UNAUTHORIZED("AUTH_002", "Unauthorized access"),
  AUTH_ACCESS_DENIED("AUTH_003", "Access denied"),
  AUTH_AUDIENCE_INVALID("AUTH_004", "Token audience does not match this API"),

  VALIDATION_FIELD_INVALID("VAL_002", "Field validation failed"),
  VALIDATION_TYPE_MISMATCH("VAL_003", "Invalid parameter type"),
  VALIDATION_JSON_PARSE("VAL_004", "Invalid JSON format"),
  VALIDATION_BAD_REQUEST("VAL_005", "Bad request"),

  NOT_FOUND_RESOURCE("NF_001", "Resource not found"),
  NOT_FOUND_ENTITY("NF_002", "Entity not found"),

  CONFLICT_DUPLICATE("CF_001", "Duplicate record exists"),
  CONFLICT_DATA_INTEGRITY("CF_002", "Data integrity violation"),

  INTERNAL_SERVER_ERROR("INT_001", "Internal server error"),
  INTERNAL_UNEXPECTED("INT_002", "Unexpected error occurred"),

  USER_ONBOARDING_INCOMPLETE("USER_002", "Onboarding wizard not completed"),

  INTERESTS_TAG_UNKNOWN("INT_TAG_001", "Unknown interest tag"),

  PRESENCE_NOT_READY("PRES_001", "User is not Ready and cannot appear in discovery"),
  PRESENCE_LOCATION_MISSING("PRES_002", "Location is required when status is Ready"),

  MOOD_BOARD_INVALID_SIZE("MOOD_001", "Mood board must contain exactly 6 images"),
  MOOD_BOARD_UNSPLASH_REQUIRED("MOOD_002", "Mood board images must be Unsplash references"),

  PING_TARGET_NOT_READY("PING_001", "Target user is not Ready"),
  PING_ALREADY_SENT("PING_002", "Ping already sent to this user"),
  PING_SELF_TARGET("PING_003", "Cannot ping yourself"),
  PING_INVALID_TRANSITION("PING_006", "Invalid ping state transition");

  private final String code;
  private final String defaultMessage;

  ErrorCode(String code, String defaultMessage) {
    this.code = code;
    this.defaultMessage = defaultMessage;
  }
}
