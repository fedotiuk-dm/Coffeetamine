package ua.coffeetamine.config;

import java.util.Optional;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import ua.coffeetamine.common.web.ProblemDetailSecurityHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * Single Spring Security configuration for all profiles — production and integration tests alike.
 * No {@code @Profile} restriction.
 *
 * <p>This filter chain declares the public/authenticated boundary. Every genuinely public endpoint
 * must be permitted here: method-level {@code @PublicEndpoint} runs <em>after</em> this chain and
 * cannot open a request the chain has already rejected for missing authentication. Role and
 * ownership enforcement lives elsewhere:
 *
 * <ul>
 *   <li><b>Role</b> — {@code @RequiresAdmin} / {@code @RequiresAuthenticated} on controllers (see
 *       {@code ua.coffeetamine.common.security}).
 *   <li><b>Ownership / IDOR</b> — {@code SecurityUtils.verifyOwnerOrAdmin(...)} in the service
 *       layer.
 * </ul>
 *
 * <p>JWT issuer, signature, timestamp, principal claim and audience validation are delegated to
 * Spring Boot's {@code spring.security.oauth2.resourceserver.jwt.*} properties in {@code
 * application.yml}. Authority mapping is owned by {@link JwtAuthorityMapper}: every authenticated
 * principal gets {@code ROLE_USER}, and {@code ROLE_ADMIN} is granted only when the JWT {@code
 * email} claim matches the backend-owned {@code app.admin-emails} allowlist (the IdP's own role
 * claims are intentionally ignored).
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AdminAllowlistProperties.class)
public class SecurityConfig {

  /**
   * Infra endpoints that don't require authentication (health, docs, swagger, error).
   * Coffeetamine-specific public endpoints (POIs, public catalog reads, …) should be added here as
   * they land.
   */
  private static final String[] PUBLIC_ENDPOINTS = {
    "/actuator/health/**",
    "/actuator/info",
    "/actuator/prometheus",
    "/error",
    "/swagger-ui/**",
    "/swagger-ui.html",
    "/v3/api-docs/**",
    "/webjars/**",
  };

  private final Optional<CorsConfigurationSource> corsConfigurationSource;
  private final ProblemDetailSecurityHandler problemDetailSecurityHandler;
  private final JwtAuthorityMapper jwtAuthorityMapper;

  public SecurityConfig(
      Optional<CorsConfigurationSource> corsConfigurationSource,
      ProblemDetailSecurityHandler problemDetailSecurityHandler,
      JwtAuthorityMapper jwtAuthorityMapper) {
    this.corsConfigurationSource = corsConfigurationSource;
    this.problemDetailSecurityHandler = problemDetailSecurityHandler;
    this.jwtAuthorityMapper = jwtAuthorityMapper;
    log.info(
        "SecurityConfig initialised, CORS enabled: {}", this.corsConfigurationSource.isPresent());
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) {
    http.csrf(AbstractHttpConfigurer::disable);
    http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

    if (corsConfigurationSource.isPresent()) {
      http.cors(cors -> cors.configurationSource(corsConfigurationSource.get()));
    } else {
      http.cors(AbstractHttpConfigurer::disable);
    }

    http.authorizeHttpRequests(
        authz ->
            authz
                .requestMatchers(HttpMethod.OPTIONS, "/**")
                .permitAll()
                .requestMatchers(PUBLIC_ENDPOINTS)
                .permitAll()
                .requestMatchers("/api/admin/**")
                .hasRole("ADMIN")
                .anyRequest()
                .authenticated());

    http.oauth2ResourceServer(
        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthorityMapper)));

    http.exceptionHandling(
        exceptions ->
            exceptions
                .authenticationEntryPoint(problemDetailSecurityHandler)
                .accessDeniedHandler(problemDetailSecurityHandler));

    return http.build();
  }
}
