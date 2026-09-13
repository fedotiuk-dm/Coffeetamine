package ua.coffeetamine.presence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound to {@code coffeetamine.presence.*}. PRODUCT_CONCEPT §10 mandates that raw GPS never leaves
 * the server: incoming raw coordinates are jittered up to {@code jitterMaxMeters} away before being
 * persisted. Tune via env var without redeploying.
 */
@ConfigurationProperties(prefix = "coffeetamine.presence")
public record PresenceProperties(double jitterMaxMeters) {

  public PresenceProperties {
    if (jitterMaxMeters <= 0 || jitterMaxMeters > 10_000) {
      throw new IllegalArgumentException(
          "coffeetamine.presence.jitter-max-meters must be in (0, 10000]: " + jitterMaxMeters);
    }
  }
}
