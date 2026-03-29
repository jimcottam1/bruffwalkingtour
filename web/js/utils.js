/**
 * Pure geo-utility functions. No DOM or browser API dependencies.
 * All logic is a direct port of LocationService.kt unless noted.
 */

import { BOUNDARY, WALKING_SPEED_MPM } from './data.js';

const EARTH_RADIUS_M = 6371000;

/**
 * Calculate the distance between two coordinates using the Haversine formula.
 * Accurate to within ~0.3% for distances under 50 km.
 * Note: Android uses Vincenty (Location.distanceBetween); the difference is
 * negligible for walking distances under 1 km.
 * @returns distance in metres
 */
export function haversineDistance(lat1, lon1, lat2, lon2) {
  const φ1 = (lat1 * Math.PI) / 180;
  const φ2 = (lat2 * Math.PI) / 180;
  const Δφ = ((lat2 - lat1) * Math.PI) / 180;
  const Δλ = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(Δφ / 2) ** 2 +
    Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/**
 * Calculate the initial bearing from point 1 to point 2.
 * @returns bearing in degrees [0, 360)
 */
export function bearingDeg(lat1, lon1, lat2, lon2) {
  const φ1 = (lat1 * Math.PI) / 180;
  const φ2 = (lat2 * Math.PI) / 180;
  const Δλ = ((lon2 - lon1) * Math.PI) / 180;
  const y = Math.sin(Δλ) * Math.cos(φ2);
  const x =
    Math.cos(φ1) * Math.sin(φ2) -
    Math.sin(φ1) * Math.cos(φ2) * Math.cos(Δλ);
  return ((Math.atan2(y, x) * 180) / Math.PI + 360) % 360;
}

/**
 * Convert a bearing to a compass direction string.
 * Exact port of LocationService.getDirectionFromBearing().
 * North wraps at <22.5 and >=337.5.
 */
export function directionFromBearing(bearing) {
  const b = (bearing + 360) % 360;
  if (b < 22.5 || b >= 337.5) return 'Head North';
  if (b < 67.5) return 'Head Northeast';
  if (b < 112.5) return 'Head East';
  if (b < 157.5) return 'Head Southeast';
  if (b < 202.5) return 'Head South';
  if (b < 247.5) return 'Head Southwest';
  if (b < 292.5) return 'Head West';
  return 'Head Northwest';
}

/**
 * Format a distance in metres for display.
 * Port of LocationService.getNavigationInstruction() formatting.
 * <50 m  → exact metres
 * <1000 m → rounded to nearest 10 m
 * ≥1000 m → X.X km
 */
export function formatDistance(metres) {
  if (metres < 50) return `${Math.round(metres)}m`;
  if (metres < 1000) return `${Math.round(metres / 10) * 10}m`;
  return `${(metres / 1000).toFixed(1)}km`;
}

/**
 * Estimate walking time to cover a distance at WALKING_SPEED_MPM.
 * @returns string like "3 min"
 */
export function formatEta(metres) {
  return `${Math.ceil(metres / WALKING_SPEED_MPM)} min`;
}

/**
 * Return true if the coordinate is outside the rectangular tour boundary.
 * Exact port of LocationService.isOutsideRectangularBoundary().
 * @param {number} lat
 * @param {number} lon
 * @param {object} boundary - defaults to BOUNDARY from data.js
 */
export function isOutsideBoundary(lat, lon, boundary = BOUNDARY) {
  const halfWidthDeg =
    boundary.WIDTH_KM / 2 /
    (111.0 * Math.cos((boundary.CENTER_LAT * Math.PI) / 180));
  const halfHeightDeg = boundary.HEIGHT_KM / 2 / 111.0;
  return (
    lat > boundary.CENTER_LAT + halfHeightDeg ||
    lat < boundary.CENTER_LAT - halfHeightDeg ||
    lon > boundary.CENTER_LON + halfWidthDeg ||
    lon < boundary.CENTER_LON - halfWidthDeg
  );
}
