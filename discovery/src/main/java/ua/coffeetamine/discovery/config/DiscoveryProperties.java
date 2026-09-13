package ua.coffeetamine.discovery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operational limits for the discovery filter. Bound to {@code coffeetamine.discovery.*}. Treated
 * as a server-side cap on whatever the client sends — any caller-supplied radius or compatibility
 * threshold is clamped into the allowed range before being applied. PRODUCT_CONCEPT §7 mandates a
 * threshold floor so the conjunctive filter (READY ∧ distance ∧ compatibility) cannot be bypassed.
 *
 * @param defaultRadiusMeters fallback when the client omits {@code radiusMeters}
 * @param maxRadiusMeters hard ceiling on the search radius (clamped server-side)
 * @param defaultMinCompatibility fallback compatibility threshold (1..100) when the client omits it
 * @param minAllowedCompatibility lower bound on the compatibility threshold the client may request
 */
@ConfigurationProperties(prefix = "coffeetamine.discovery")
public record DiscoveryProperties(
    int defaultRadiusMeters,
    int maxRadiusMeters,
    int defaultMinCompatibility,
    int minAllowedCompatibility) {

  public DiscoveryProperties {
    if (defaultRadiusMeters <= 0 || maxRadiusMeters <= 0 || defaultRadiusMeters > maxRadiusMeters) {
      throw new IllegalArgumentException(
          "coffeetamine.discovery radius config invalid: default="
              + defaultRadiusMeters
              + ", max="
              + maxRadiusMeters);
    }
    if (defaultMinCompatibility > 100
        || minAllowedCompatibility < 1
        || minAllowedCompatibility > 100
        || defaultMinCompatibility < minAllowedCompatibility) {
      throw new IllegalArgumentException(
          "coffeetamine.discovery compatibility config invalid: default="
              + defaultMinCompatibility
              + ", minAllowed="
              + minAllowedCompatibility);
    }
  }
}
