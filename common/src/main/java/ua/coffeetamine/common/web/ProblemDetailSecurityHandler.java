package ua.coffeetamine.common.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import ua.coffeetamine.common.exception.ErrorCode;
import ua.coffeetamine.common.exception.ProblemDetails;

import lombok.extern.slf4j.Slf4j;

import org.jspecify.annotations.NonNull;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unifies {@code 401} (no/invalid token) and {@code 403} (insufficient role) responses from the
 * security filter chain into the same RFC 7807 {@code application/problem+json} shape produced by
 * {@link ua.coffeetamine.common.exception.GlobalExceptionHandler}, so clients see one error
 * contract regardless of which layer rejected the request.
 *
 * <p>Mounted from {@code SecurityConfig} as both the {@link AuthenticationEntryPoint} (401) and the
 * {@link AccessDeniedHandler} (403). These hooks run <em>before</em> the controller advice, so we
 * replicate the {@link ProblemDetails} formatting here directly.
 *
 * <p>Uses Jackson 3 ({@link JsonMapper}) — the Spring Boot 4 default. The auto-wired mapper is
 * rebuilt with {@link ProblemDetailJacksonMixin} so the {@code status} property lands in the JSON
 * output (Jackson would otherwise serialise the {@code int status} getter under a different name).
 */
@Component
@Slf4j
public class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final JsonMapper jsonMapper;

  public ProblemDetailSecurityHandler(JsonMapper jsonMapper) {
    this.jsonMapper =
        jsonMapper.rebuild().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();
  }

  @Override
  public void commence(
      HttpServletRequest request,
      @NonNull HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    log.debug(
        "Authentication failed: {} {} - {}",
        request.getMethod(),
        request.getRequestURI(),
        authException.getMessage());
    write(
        request,
        response,
        ProblemDetails.of(
            HttpStatus.UNAUTHORIZED,
            "Authentication Required",
            "Authentication required",
            "unauthorized",
            ErrorCode.AUTH_LOGIN_FAILED));
  }

  @Override
  public void handle(
      HttpServletRequest request,
      @NonNull HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    log.warn(
        "Access denied: {} {} - {}",
        request.getMethod(),
        request.getRequestURI(),
        accessDeniedException.getMessage());
    write(
        request,
        response,
        ProblemDetails.of(
            HttpStatus.FORBIDDEN,
            "Access Denied",
            "Access denied",
            "forbidden",
            ErrorCode.AUTH_ACCESS_DENIED));
  }

  private void write(
      HttpServletRequest request, HttpServletResponse response, ProblemDetail problem)
      throws IOException {
    if (response.isCommitted()) {
      return;
    }
    ProblemDetails.addInstance(problem, request.getRequestURI());
    ProblemDetails.addTraceId(problem);
    response.setStatus(problem.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    jsonMapper.writeValue(response.getOutputStream(), problem);
  }
}
