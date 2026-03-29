/**
 * Walking-route service using the OSRM public API (free, no key required).
 * Direct port of RouteService.kt.
 *
 * OSRM returns geometry coordinates as [longitude, latitude].
 * All functions in this module return [latitude, longitude] pairs for Leaflet.
 */

const OSRM_BASE = 'https://router.project-osrm.org';

/**
 * Fetch a real walking route from OSRM.
 * @returns {Promise<Array<[number, number]>>} array of [lat, lon] pairs
 * @throws on network error or empty response
 */
export async function fetchRoute(fromLat, fromLon, toLat, toLon) {
  const coords = `${fromLon},${fromLat};${toLon},${toLat}`;
  const url = `${OSRM_BASE}/route/v1/foot/${coords}?geometries=geojson&overview=full`;
  const response = await fetch(url);
  if (!response.ok) throw new Error(`OSRM returned ${response.status}`);
  const data = await response.json();
  if (!data.routes?.length) throw new Error('OSRM returned no routes');
  // Flip [lon, lat] → [lat, lon]
  return data.routes[0].geometry.coordinates.map(([lon, lat]) => [lat, lon]);
}

/**
 * L-shaped fallback route. Direct port of RouteService.getSimpleRoute().
 * Three cases: very short (direct line), N-S dominant, E-W dominant.
 * @returns {Array<[number, number]>} array of [lat, lon] pairs
 */
export function fallbackRoute(fromLat, fromLon, toLat, toLon) {
  const latDiff = toLat - fromLat;
  const lonDiff = toLon - fromLon;
  const totalDist = Math.sqrt(latDiff ** 2 + lonDiff ** 2);

  if (totalDist < 0.0005) {
    return [
      [fromLat, fromLon],
      [toLat, toLon],
    ];
  }

  if (Math.abs(latDiff) > Math.abs(lonDiff)) {
    // Primarily north-south
    const mid = [fromLat + latDiff * 0.7, fromLon];
    return [[fromLat, fromLon], mid, [mid[0], toLon], [toLat, toLon]];
  }

  // Primarily east-west
  const mid = [fromLat, fromLon + lonDiff * 0.7];
  return [[fromLat, fromLon], mid, [toLat, mid[1]], [toLat, toLon]];
}

/**
 * Get a walking route, transparently falling back to the L-shaped route
 * if OSRM is unavailable (offline or error).
 * @returns {Promise<Array<[number, number]>>} array of [lat, lon] pairs
 */
export async function getRoute(fromLat, fromLon, toLat, toLon) {
  try {
    return await fetchRoute(fromLat, fromLon, toLat, toLon);
  } catch {
    return fallbackRoute(fromLat, fromLon, toLat, toLon);
  }
}
