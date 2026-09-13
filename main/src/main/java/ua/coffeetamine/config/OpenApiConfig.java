package ua.coffeetamine.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Coffeetamine OpenAPI scaffold.
 *
 * <p>This class declares only the cross-cutting metadata that must live in Java — application info
 * and the {@code bearerAuth} security scheme so Swagger UI's <em>Authorize</em> button accepts an
 * OIDC-issued JWT. Endpoint grouping (per-module Swagger tabs) is configured declaratively in
 * {@code application.yml} under {@code springdoc.group-configs} as feature modules land.
 */
@Configuration
public class OpenApiConfig {

  private static final String BEARER_SCHEME = "bearerAuth";

  @Value("${info.app.name:Coffeetamine}")
  private String appName;

  @Value("${info.app.version:0.1.0-SNAPSHOT}")
  private String appVersion;

  @Bean
  public OpenAPI coffeetamineOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title(appName + " API")
                .version(appVersion)
                .description(
                    "Location-based social discovery for vibe-based coffee micro-encounters."))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description(
                            """
                            OIDC-issued JWT, sent as `Authorization: Bearer <token>`.

                            Production: OIDC Authorization Code + PKCE through any \
                            spec-compliant OIDC provider (ZITADEL, Auth0, etc.) with \
                            Google as a federated IdP — see \
                            `architecture/MOBILE_OIDC_GOOGLE.md`.

                            Dev: run `make token` (or `make token-admin`) on the host \
                            and paste the raw token here — no `Bearer ` prefix.""")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
  }
}
