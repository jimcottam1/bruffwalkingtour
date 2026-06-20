/**
 * Tests for map.js
 *
 * Leaflet (global `L`) is mocked below so these tests run in Node/jsdom
 * without a real browser or network. Each test gets a fresh map instance
 * via destroyMap() in beforeEach.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  initMap,
  destroyMap,
  addWaypointMarkers,
  updateUserLocation,
  drawRoute,
  panTo,
  recentre,
  isFollowing,
} from '../js/map.js';
import { WAYPOINTS, BOUNDARY } from '../js/data.js';

// ---------------------------------------------------------------------------
// Leaflet mock
// ---------------------------------------------------------------------------

function makeMarkerMock(lat, lon) {
  const m = {
    _lat: lat,
    _lon: lon,
    getLatLng: vi.fn(() => ({ lat, lng: lon })),
    setLatLng: vi.fn((ll) => { m._lat = ll[0]; m._lon = ll[1]; }),
    remove: vi.fn(),
    bindPopup: vi.fn().mockReturnThis(),
    addTo: vi.fn().mockReturnThis(),
  };
  return m;
}

function makePolylineMock() {
  const p = { remove: vi.fn(), addTo: vi.fn().mockReturnThis() };
  return p;
}

function makeRectangleMock() {
  return { addTo: vi.fn().mockReturnThis() };
}

let _listeners = {};
let _lastPanTo  = null;
let _lastView   = null;

const mapMock = {
  setView: vi.fn((center, zoom) => { _lastView = { center, zoom }; }),
  panTo:   vi.fn((ll, opts) => { _lastPanTo = { ll, opts }; }),
  on:      vi.fn((event, cb) => { _listeners[event] = cb; }),
  remove:  vi.fn(),
};

// Expose a way for tests to trigger Leaflet events
function triggerMapEvent(event) {
  _listeners[event]?.();
}

const circleMock = {
  setLatLng: vi.fn(),
  getLatLng: vi.fn(() => ({ lat: 52.477, lng: -8.548 })),
  addTo: vi.fn().mockReturnThis(),
};

function setupLeafletMock() {
  _listeners = {};
  _lastPanTo  = null;
  _lastView   = null;
  mapMock.setView.mockClear();
  mapMock.panTo.mockClear();
  mapMock.on.mockClear();
  mapMock.remove.mockClear();
  circleMock.setLatLng.mockClear();
  circleMock.addTo.mockClear();

  global.L = {
    map: vi.fn(() => mapMock),
    tileLayer: vi.fn(() => ({ addTo: vi.fn() })),
    divIcon: vi.fn((opts) => opts),
    marker: vi.fn((ll) => makeMarkerMock(ll[0], ll[1])),
    circleMarker: vi.fn(() => circleMock),
    polyline: vi.fn(() => makePolylineMock()),
    rectangle: vi.fn(() => makeRectangleMock()),
  };
}

// ---------------------------------------------------------------------------
// Setup / teardown
// ---------------------------------------------------------------------------

beforeEach(() => {
  setupLeafletMock();
  destroyMap(); // ensure clean state
});

afterEach(() => {
  destroyMap();
  delete global.L;
});

// ---------------------------------------------------------------------------
// initMap
// ---------------------------------------------------------------------------

describe('initMap', () => {
  it('calls L.map with the given element ID', () => {
    initMap('map');
    expect(L.map).toHaveBeenCalledWith('map', expect.any(Object));
  });

  it('centres the map on BOUNDARY.CENTER_LAT/LON', () => {
    initMap('map');
    const [center, zoom] = mapMock.setView.mock.calls[0];
    expect(center[0]).toBeCloseTo(BOUNDARY.CENTER_LAT, 5);
    expect(center[1]).toBeCloseTo(BOUNDARY.CENTER_LON, 5);
    expect(zoom).toBe(17.5);
  });

  it('returns the same map instance on repeated calls', () => {
    const m1 = initMap('map');
    const m2 = initMap('map');
    expect(m1).toBe(m2);
    expect(L.map).toHaveBeenCalledTimes(1);
  });

  it('registers a dragstart listener for follow mode', () => {
    initMap('map');
    expect(mapMock.on).toHaveBeenCalledWith('dragstart', expect.any(Function));
  });

  it('calls the onFollowChange callback when drag starts', () => {
    const cb = vi.fn();
    initMap('map', cb);
    triggerMapEvent('dragstart');
    expect(cb).toHaveBeenCalledWith(false);
  });
});

// ---------------------------------------------------------------------------
// destroyMap
// ---------------------------------------------------------------------------

describe('destroyMap', () => {
  it('calls map.remove()', () => {
    initMap('map');
    destroyMap();
    expect(mapMock.remove).toHaveBeenCalledTimes(1);
  });

  it('after destroyMap, initMap creates a new map', () => {
    initMap('map');
    destroyMap();
    initMap('map');
    expect(L.map).toHaveBeenCalledTimes(2);
  });

  it('does not throw when called without initMap', () => {
    expect(() => destroyMap()).not.toThrow();
  });
});

// ---------------------------------------------------------------------------
// addWaypointMarkers
// ---------------------------------------------------------------------------

describe('addWaypointMarkers', () => {
  beforeEach(() => initMap('map'));

  it('creates a marker for each waypoint', () => {
    addWaypointMarkers(WAYPOINTS, 0, []);
    expect(L.marker).toHaveBeenCalledTimes(WAYPOINTS.length);
  });

  it('places markers at the correct coordinates', () => {
    addWaypointMarkers(WAYPOINTS, 0, []);
    WAYPOINTS.forEach((wp, i) => {
      const [ll] = L.marker.mock.calls[i];
      expect(ll[0]).toBeCloseTo(wp.latitude, 5);
      expect(ll[1]).toBeCloseTo(wp.longitude, 5);
    });
  });

  it('assigns marker-current class to the current waypoint', () => {
    addWaypointMarkers(WAYPOINTS, 1, []);
    const divIconCall = L.divIcon.mock.calls[1][0];
    expect(divIconCall.html).toContain('marker-current');
  });

  it('assigns marker-visited class to visited waypoints', () => {
    addWaypointMarkers(WAYPOINTS, 2, ['thomas_fitzgerald_centre']);
    const divIconCall = L.divIcon.mock.calls[0][0];
    expect(divIconCall.html).toContain('marker-visited');
  });

  it('assigns marker-future class to future waypoints', () => {
    addWaypointMarkers(WAYPOINTS, 0, []);
    const divIconCall = L.divIcon.mock.calls[3][0]; // last waypoint
    expect(divIconCall.html).toContain('marker-future');
  });

  it('removes previous markers before adding new ones', () => {
    addWaypointMarkers(WAYPOINTS, 0, []);
    const firstMarker = L.marker.mock.results[0].value;
    addWaypointMarkers(WAYPOINTS, 1, [WAYPOINTS[0].id]);
    expect(firstMarker.remove).toHaveBeenCalledTimes(1);
  });

  it('includes the waypoint number in the marker HTML', () => {
    addWaypointMarkers(WAYPOINTS, 0, []);
    const html = L.divIcon.mock.calls[2][0].html;
    expect(html).toContain('3'); // waypoint index 2 → number 3
  });
});

// ---------------------------------------------------------------------------
// updateUserLocation
// ---------------------------------------------------------------------------

describe('updateUserLocation', () => {
  beforeEach(() => initMap('map'));

  it('creates a circleMarker on first call', () => {
    updateUserLocation(52.477, -8.548);
    expect(L.circleMarker).toHaveBeenCalledWith([52.477, -8.548], expect.any(Object));
  });

  it('moves the existing marker on subsequent calls (does not create a new one)', () => {
    updateUserLocation(52.477, -8.548);
    updateUserLocation(52.478, -8.549);
    expect(L.circleMarker).toHaveBeenCalledTimes(1);
    expect(circleMock.setLatLng).toHaveBeenCalledWith([52.478, -8.549]);
  });

  it('does not throw when called before initMap', () => {
    destroyMap();
    expect(() => updateUserLocation(52.477, -8.548)).not.toThrow();
  });
});

// ---------------------------------------------------------------------------
// drawRoute
// ---------------------------------------------------------------------------

describe('drawRoute', () => {
  beforeEach(() => initMap('map'));

  it('creates a polyline with the given points', () => {
    const pts = [[52.477, -8.548], [52.478, -8.549]];
    drawRoute(pts);
    expect(L.polyline).toHaveBeenCalledWith(pts, expect.any(Object));
  });

  it('removes the previous polyline before drawing a new one', () => {
    const pts = [[52.477, -8.548], [52.478, -8.549]];
    drawRoute(pts);
    const firstPoly = L.polyline.mock.results[0].value;
    drawRoute([[52.479, -8.550], [52.480, -8.551]]);
    expect(firstPoly.remove).toHaveBeenCalledTimes(1);
  });

  it('does not draw when given fewer than 2 points', () => {
    drawRoute([[52.477, -8.548]]);
    expect(L.polyline).not.toHaveBeenCalled();
  });

  it('does not draw when given null', () => {
    drawRoute(null);
    expect(L.polyline).not.toHaveBeenCalled();
  });

  it('does not throw when called before initMap', () => {
    destroyMap();
    expect(() => drawRoute([[52.477, -8.548], [52.478, -8.549]])).not.toThrow();
  });
});

// ---------------------------------------------------------------------------
// panTo and follow mode
// ---------------------------------------------------------------------------

describe('panTo and follow mode', () => {
  beforeEach(() => initMap('map'));

  it('pans the map when follow mode is active (default)', () => {
    expect(isFollowing()).toBe(true);
    panTo(52.477, -8.548);
    expect(mapMock.panTo).toHaveBeenCalledWith([52.477, -8.548], expect.any(Object));
  });

  it('does NOT pan when user has dragged the map (follow mode off)', () => {
    triggerMapEvent('dragstart'); // simulate manual pan
    expect(isFollowing()).toBe(false);
    panTo(52.477, -8.548);
    expect(mapMock.panTo).not.toHaveBeenCalled();
  });

  it('recentre() restores follow mode', () => {
    triggerMapEvent('dragstart');
    expect(isFollowing()).toBe(false);
    recentre();
    expect(isFollowing()).toBe(true);
  });

  it('recentre() pans to the last known user position', () => {
    updateUserLocation(52.477, -8.548);
    triggerMapEvent('dragstart');
    recentre();
    expect(mapMock.panTo).toHaveBeenCalledWith(
      expect.objectContaining({ lat: 52.477 }),
      expect.any(Object),
    );
  });

  it('panTo does not throw when called before initMap', () => {
    destroyMap();
    expect(() => panTo(52.477, -8.548)).not.toThrow();
  });

  it('onFollowChange callback fires once on first drag, not again on second drag', () => {
    const cb = vi.fn();
    destroyMap();
    initMap('map', cb);
    triggerMapEvent('dragstart'); // follow: false
    triggerMapEvent('dragstart'); // already false — no change
    expect(cb).toHaveBeenCalledTimes(1);
    expect(cb).toHaveBeenCalledWith(false);
  });

  it('onFollowChange fires with true after recentre()', () => {
    const cb = vi.fn();
    destroyMap();
    initMap('map', cb);
    triggerMapEvent('dragstart'); // false
    cb.mockClear();
    recentre(); // true
    expect(cb).toHaveBeenCalledWith(true);
  });
});

// ---------------------------------------------------------------------------
// isFollowing
// ---------------------------------------------------------------------------

describe('isFollowing', () => {
  it('returns true by default after initMap', () => {
    initMap('map');
    expect(isFollowing()).toBe(true);
  });

  it('returns false after a drag event', () => {
    initMap('map');
    triggerMapEvent('dragstart');
    expect(isFollowing()).toBe(false);
  });

  it('returns true again after recentre()', () => {
    initMap('map');
    triggerMapEvent('dragstart');
    recentre();
    expect(isFollowing()).toBe(true);
  });

  it('returns true after destroyMap + initMap (state is reset)', () => {
    initMap('map');
    triggerMapEvent('dragstart');
    destroyMap();
    initMap('map');
    expect(isFollowing()).toBe(true);
  });
});
