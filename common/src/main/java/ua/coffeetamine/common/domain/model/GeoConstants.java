package ua.coffeetamine.common.domain.model;

import lombok.experimental.UtilityClass;

@UtilityClass
public class GeoConstants {

  /** Mean Earth radius (WGS-84 spherical approximation), in meters. */
  public final double EARTH_RADIUS_METERS = 6_371_000.0;

  /** Meters per degree of latitude (constant — longitude varies with cos(lat)). */
  public final double METERS_PER_LATITUDE_DEGREE = 111_320.0;

  /** Lower bound on cos(latitude) to avoid division blow-up near the poles. */
  public final double MIN_LONGITUDE_COSINE = 0.01;

  /** Meters per degree of longitude at the given latitude, floored near the poles. */
  public double metersPerLongitudeDegree(double latitudeDegrees) {
    return METERS_PER_LATITUDE_DEGREE
        * Math.max(Math.cos(Math.toRadians(latitudeDegrees)), MIN_LONGITUDE_COSINE);
  }
}
