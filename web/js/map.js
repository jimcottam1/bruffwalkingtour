/**
 * MapController wraps Leaflet.js.
 * Leaflet is expected as a global `L` loaded via <script> tag in the HTML.
 *
 * Guard: calling initMap() twice on the same element throws in Leaflet.
 * The module stores the instance and returns it on repeated calls.
 */

import { BOUNDARY } from './data.js';

let _map = null;
let _userMarker = null;
let _routePolyline = null;
const _waypointMarkers = [];

/**
 * Initialise the Leaflet map. Safe to call more than once — returns existing
 * instance on repeat calls.
 * @param {string} elementId - ID of the container <div>
 * @returns the Leaflet map instance
 */
export function initMap(elementId) {
  if (_map) return _map;

  _map = L.map(elementId, { zoomControl: true });

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution:
      '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    maxZoom: 20,
  }).addTo(_map);

  // Start centred on Sean Wall Monument at street level — matches Android setupMap()
  _map.setView([BOUNDARY.CENTER_LAT, BOUNDARY.CENTER_LON], 17.5);
  _drawBoundary();

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
  }
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
      .bindPopup(`<strong>${wp.name}</strong>`);

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

/** Pan the map to a coordinate with animation. */
export function panTo(lat, lon) {
  _map?.panTo([lat, lon], { animate: true, duration: 0.5 });
}

// Internal: draw the rectangular tour boundary
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
