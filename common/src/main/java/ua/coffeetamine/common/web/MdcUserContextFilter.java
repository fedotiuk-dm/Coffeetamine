package ua.coffeetamine.common.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import ua.coffeetamine.common.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;

/**
 * Places the resolved internal {@code app_user_id} UUID into MDC under {@code userId} so log lines
 * emitted while handling an authenticated request carry the principal. The IdP-specific JWT subject
 * is intentionally not used — see {@link SecurityUtils#getCurrentUserId()}. The logback pattern
 * combines this with {@code traceId}/{@code spanId} (placed by micrometer-tracing) under {@code
 * logging.pattern.correlation}.
 */
@RequiredArgsConstructor
public class MdcUserContextFilter extends OncePerRequestFilter {

  public static final String USER_ID_KEY = "userId";

  private final SecurityUtils securityUtils;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    placeUserId();
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(USER_ID_KEY);
    }
  }

  private void placeUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated()) {
      MDC.put(USER_ID_KEY, securityUtils.getCurrentUserId().toString());
    }
  }
}
