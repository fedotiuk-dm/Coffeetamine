package ua.coffeetamine.common.exception;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import lombok.experimental.UtilityClass;

import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

/**
 * Factory + helpers for RFC 7807 {@link ProblemDetail} instances used across the platform.
 *
 * <p>Every problem produced through this factory carries:
 *
 * <ul>
 *   <li>a stable {@code type} URI under {@link #ERROR_TYPE_BASE} (clients can dispatch on it),
 *   <li>a machine-readable {@code errorCode} property ({@link ErrorCode#getCode()}),
 *   <li>optionally a {@code traceId} property pulled from MDC,
 *   <li>optionally an {@code instance} URI = current request path.
 * </ul>
 */
@UtilityClass
public class ProblemDetails {

  public final String ERROR_CODE_KEY = "errorCode";
  public final String TRACE_ID_KEY = "traceId";

  private final URI ERROR_TYPE_BASE = URI.create("https://coffeetamine.app/errors/");

  public ProblemDetail of(
      HttpStatus status,
      String title,
      @Nullable String detail,
      String typeSlug,
      ErrorCode errorCode) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(status, detailOrDefault(detail, errorCode));
    problem.setTitle(title);
    problem.setType(type(typeSlug));
    problem.setProperty(ERROR_CODE_KEY, errorCode.getCode());
    return problem;
  }

  public URI type(String slug) {
    return ERROR_TYPE_BASE.resolve(slug);
  }

  public void addTraceId(ProblemDetail problem) {
    String traceId = MDC.get(TRACE_ID_KEY);
    if (hasText(traceId)) {
      problem.setProperty(TRACE_ID_KEY, traceId);
    }
  }

  public void addCurrentRequestInstance(ProblemDetail problem) {
    if (problem.getInstance() != null) {
      return;
    }
    if (RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes servletAttrs) {
      addInstance(problem, servletAttrs.getRequest().getRequestURI());
    }
  }

  /**
   * Sets the {@code instance} URI from a known request path. Use this from the security filter
   * chain ({@code ProblemDetailSecurityHandler}) where {@link RequestContextHolder} may not yet be
   * populated.
   */
  public void addInstance(ProblemDetail problem, @Nullable String requestUri) {
    if (requestUri == null || requestUri.isBlank()) {
      return;
    }
    problem.setInstance(URI.create(requestUri));
  }

  public boolean hasText(@Nullable String value) {
    return value != null && !value.isBlank();
  }

  private String detailOrDefault(@Nullable String detail, ErrorCode errorCode) {
    return hasText(detail) ? detail : errorCode.getDefaultMessage();
  }
}
