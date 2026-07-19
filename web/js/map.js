/**
 * MapController wraps Leaflet.js.
 * Leaflet is expected as a global `L` loaded via <script> tag in the HTML.
 *
 * Guard: calling initMap() twice on the same element throws in Leaflet.
 * The module stores the instance and returns it on repeated calls.
 *
 * Follow mode: the map pans to the user's position automatically until the
 * user manually drags/pans, at which point follow mode is suspended. A
 * re-centre button (managed by the caller) can restore follow mode.
 */

import { BOUNDARY } from './data.js';

let _map = null;
let _userMarker = null;
let _routePolyline = null;
const _waypointMarkers = [];

let _followMode = true;
let _onFollowChange = null; // callback(isFollowing: boolean)

/**
 * Initialise the Leaflet map. Safe to call more than once — returns existing
 * instance on repeat calls.
 * @param {string} elementId - ID of the container <div>
 * @param {function} [onFollowChange] - called when follow mode changes
 * @returns the Leaflet map instance
 */
export function initMap(elementId, onFollowChange = null) {
  if (_map) return _map;

  _onFollowChange = onFollowChange;

  _map = L.map(elementId, { zoomControl: true });

  // OSM's raw tile.openstreetmap.org still blocked this app's traffic even
  // after switching to the canonical URL and a compliant User-Agent —
  // confirmed on two separate real devices on two separate networks. CARTO's
  // basemap tiles are free, keyless, OSM-derived, and meant for exactly this
  // kind of embedding. https://operations.osmfoundation.org/policies/tiles/
  //
  // Voyager (not the more muted Positron style) for visible road/building/park
  // contrast. The {r} token resolves to '@2x' on retina displays (based on
  // Browser.retina) and CARTO serves genuine higher-resolution tiles at that
  // URL — deliberately NOT using the separate detectRetina option, which
  // instead fetches a deeper zoom level and halves tileSize as a fallback for
  // servers without native @2x tiles; combining both would double up wrongly.
  L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
    attribution:
      '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors © <a href="https://carto.com/attributions">CARTO</a>',
    subdomains: 'abcd',
    maxZoom: 20,
  }).addTo(_map);

  // Start centred on Sean Wall Monument at street level — matches Android setupMap()
  _map.setView([BOUNDARY.CENTER_LAT, BOUNDARY.CENTER_LON], 17.5);
  _drawBoundary();

  // When the user manually drags the map, suspend follow mode
  _map.on('dragstart', () => _setFollowMode(false));

  return _map;
}

/**
 * Fully remove the map. Call this before re-initialising on the same element.
 */
export function destroyMap() {
  if (_map) {
    _map.remove();
    _map = null;
    _userMarker = null;
    _routePolyline = null;
    _waypointMarkers.length = 0;
    _followMode = true;
    _onFollowChange = null;
  }
}

/**
 * Re-enable automatic map-following and pan to the user's last known position.
 * Call this when the user taps the re-centre button.
 */
export function recentre() {
  _setFollowMode(true);
  if (_userMarker) {
    _map?.panTo(_userMarker.getLatLng(), { animate: true, duration: 0.5 });
  }
}

/** Returns true when the map is currently following the user's position. */
export function isFollowing() {
  return _followMode;
}

/**
 * Render numbered waypoint markers. Replaces any existing markers.
 * @param {Array}  waypoints    - full waypoints array
 * @param {number} currentIndex - index of the waypoint the user is heading to
 * @param {Array}  visitedIds   - array of already-visited waypoint IDs
 */
export function addWaypointMarkers(waypoints, currentIndex, visitedIds = []) {
  _waypointMarkers.forEach((m) => m.remove());
  _waypointMarkers.length = 0;
  if (!_map) return;

  waypoints.forEach((wp, i) => {
    const isVisited = visitedIds.includes(wp.id);
    const isCurrent = i === currentIndex;
    const cls = isVisited
      ? 'marker-visited'
      : isCurrent
      ? 'marker-current'
      : 'marker-future';

    const icon = L.divIcon({
      className: '',
      html: `<div class="waypoint-marker ${cls}">${i + 1}</div>`,
      iconSize: [36, 36],
      iconAnchor: [18, 18],
    });

    const marker = L.marker([wp.latitude, wp.longitude], { icon })
      .addTo(_map)
      .bindPopup(`<strong>${wp.name}</strong><br><small>${wp.description}</small>`);

    _waypointMarkers.push(marker);
  });
}

/**
 * Move (or create) the user location dot.
 */
export function updateUserLocation(lat, lon) {
  if (!_map) return;
  if (_userMarker) {
    _userMarker.setLatLng([lat, lon]);
  } else {
    _userMarker = L.circleMarker([lat, lon], {
      radius: 10,
      fillColor: '#4a90d9',
      color: '#ffffff',
      weight: 2,
      opacity: 1,
      fillOpacity: 0.9,
    }).addTo(_map);
  }
}

/**
 * Draw the walking route polyline, replacing any previous one.
 * @param {Array<[number, number]>} latLonArray - array of [lat, lon] pairs
 */
export function drawRoute(latLonArray) {
  if (!_map) return;
  if (_routePolyline) {
    _routePolyline.remove();
    _routePolyline = null;
  }
  if (latLonArray?.length > 1) {
    _routePolyline = L.polyline(latLonArray, {
      color: '#c8922a',
      weight: 4,
      opacity: 0.8,
      dashArray: '8, 6',
    }).addTo(_map);
  }
}

/**
 * Pan the map to a coordinate — only if follow mode is active.
 * Callers should always call panTo; this function decides whether to act.
 */
export function panTo(lat, lon) {
  if (!_followMode || !_map) return;
  _map.panTo([lat, lon], { animate: true, duration: 0.5 });
}

// ---- Internal helpers -------------------------------------------------------

function _setFollowMode(following) {
  if (_followMode === following) return;
  _followMode = following;
  _onFollowChange?.(following);
}

function _drawBoundary() {
  const halfWidthDeg =
    BOUNDARY.WIDTH_KM /
    2 /
    (111.0 * Math.cos((BOUNDARY.CENTER_LAT * Math.PI) / 180));
  const halfHeightDeg = BOUNDARY.HEIGHT_KM / 2 / 111.0;
  L.rectangle(
    [
      [BOUNDARY.CENTER_LAT - halfHeightDeg, BOUNDARY.CENTER_LON - halfWidthDeg],
      [BOUNDARY.CENTER_LAT + halfHeightDeg, BOUNDARY.CENTER_LON + halfWidthDeg],
    ],
    {
      color: '#c8922a',
      weight: 1,
      opacity: 0.4,
      fillOpacity: 0.04,
      dashArray: '6, 6',
    },
  ).addTo(_map);
}
