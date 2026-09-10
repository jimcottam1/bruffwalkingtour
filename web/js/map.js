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
let _userMarkerHalo = null;
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

  // Basemap history: tile.openstreetmap.org blocked this app's traffic on real
  // devices; CARTO's keyless basemap (basemaps.cartocdn.com) then started
  // serving an "API key required" watermark tile once an IP passed its
  // anonymous quota. OpenStreetMap France's Humanitarian (HOT) style is
  // keyless, a separate community deployment, and fine for light embedded use.
  // No @2x tiles, so no {r} token. https://wiki.openstreetmap.org/wiki/OpenStreetMap_France
  L.tileLayer('https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png', {
    attribution:
      '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors — tiles courtesy of <a href="https://openstreetmap.fr/">OpenStreetMap France</a>',
    subdomains: 'abc',
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
    _userMarkerHalo = null;
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

    const size = isCurrent ? 44 : 36;
    const icon = L.divIcon({
      className: '',
      html: `<div class="waypoint-marker ${cls}">${i + 1}</div>`,
      iconSize: [size, size],
      iconAnchor: [size / 2, size / 2],
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
    _userMarkerHalo?.setLatLng([lat, lon]);
  } else {
    // Halo first so it renders beneath the solid dot.
    _userMarkerHalo = L.circleMarker([lat, lon], {
      radius: 18,
      className: 'user-dot-halo',
      fillColor: '#4a90d9',
      color: 'transparent',
      fillOpacity: 0.35,
      interactive: false,
    }).addTo(_map);
    _userMarker = L.circleMarker([lat, lon], {
      radius: 10,
      className: 'user-dot',
      fillColor: '#4a90d9',
      color: '#ffffff',
      weight: 3,
      opacity: 1,
      fillOpacity: 0.95,
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
