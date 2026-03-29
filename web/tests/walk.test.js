/**
 * Simulated Walk Test — Bruff Heritage Trail
 *
 * Feeds real GPS coordinates through TourController and utils, simulating
 * a user physically walking from 2 km north of Bruff through all 4 heritage
 * sites in sequence. Proves the complete tour flow end-to-end.
 *
 * Coordinate derivation (all on the same longitude as each target):
 *   1 degree latitude ≈ 111 000 m
 *   Distances verified against haversineDistance before inclusion.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { TourController } from '../js/tour.js';
import { WAYPOINTS, BOUNDARY } from '../js/data.js';
import {
  haversineDistance,
  bearingDeg,
  directionFromBearing,
  formatDistance,
  isOutsideBoundary,
} from '../js/utils.js';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

class MockStorage {
  constructor() { this._d = {}; }
  getItem(k) { return this._d[k] ?? null; }
  setItem(k, v) { this._d[k] = String(v); }
  removeItem(k) { delete this._d[k]; }
  clear() { this._d = {}; }
}

function makeTour(storage) {
  return new TourController(WAYPOINTS, storage);
}

// Convenience: simulate a position update and check proximity
function step(tour, lat, lon) {
  return {
    arrived: tour.checkProximity(lat, lon),
    distance: tour.getDistanceToCurrent(lat, lon),
    outside: isOutsideBoundary(lat, lon),
  };
}

// ---------------------------------------------------------------------------
// GPS route — 16 fixes covering the full walk
// (Each coordinate is verified to be the stated distance from the target WP)
// ---------------------------------------------------------------------------

const TF   = WAYPOINTS[0]; // Thomas Fitzgerald Centre   lat 52.478689 lon -8.548776  r=25m
const CH   = WAYPOINTS[1]; // Church                     lat 52.478558 lon -8.548009  r=20m
const SW   = WAYPOINTS[2]; // Sean Wall Monument          lat 52.477636 lon -8.547905  r=15m
const GAA  = WAYPOINTS[3]; // Bruff GAA Grounds           lat 52.476002 lon -8.541206  r=30m

// Steps 1-2: outside boundary (~2 km north)
const S1  = { lat: 52.495871, lon: -8.548776 }; // ~1910 m to TF, north of boundary
const S2  = { lat: 52.494000, lon: -8.548776 }; // ~1703 m to TF, still north

// Step 3: just inside north boundary edge
const S3  = { lat: 52.489600, lon: -8.548776 }; // ~1213 m to TF, boundary northEdge ≈ 52.4914

// Step 4: heading into town (not near any WP)
const S4  = { lat: 52.480000, lon: -8.548776 }; // ~146 m to TF

// Step 5: 30 m north of TF Centre — outside 25 m radius
const S5  = { lat: 52.478959, lon: -8.548776 }; // haversine ≈ 30 m to TF

// Step 6: 20 m north of TF Centre — within 25 m radius  ← ARRIVE TF
const S6  = { lat: 52.478869, lon: -8.548776 }; // haversine ≈ 20 m to TF

// Step 7: duplicate GPS fix (same spot after marking TF visited)
const S7  = { lat: 52.478869, lon: -8.548776 }; // now current=CH; ≈ 51 m to CH

// Step 8: midpoint between TF and Church — 27 m from Church, outside 20 m
const S8  = { lat: 52.478623, lon: -8.548393 }; // haversine ≈ 27 m to CH

// Step 9: 25 m north of Church — outside 20 m radius
const S9  = { lat: 52.478783, lon: -8.548009 }; // haversine ≈ 25 m to CH

// Step 10: 15 m north of Church — within 20 m radius  ← ARRIVE CHURCH
const S10 = { lat: 52.478693, lon: -8.548009 }; // haversine ≈ 15 m to CH

// Step 11: 40 m south toward Sean Wall — outside 15 m radius
const S11 = { lat: 52.478000, lon: -8.547905 }; // haversine ≈ 40 m to SW

// Step 12: 20 m north of Sean Wall — outside 15 m radius
const S12 = { lat: 52.477816, lon: -8.547905 }; // haversine ≈ 20 m to SW

// Step 13: 10 m north of Sean Wall — within 15 m radius  ← ARRIVE SEAN WALL
const S13 = { lat: 52.477726, lon: -8.547905 }; // haversine ≈ 10 m to SW

// Step 14: midpoint walking east to GAA
const S14 = { lat: 52.477000, lon: -8.544556 }; // haversine ≈ 253 m to GAA

// Step 15: 35 m north of GAA — outside 30 m radius
const S15 = { lat: 52.476317, lon: -8.541206 }; // haversine ≈ 35 m to GAA

// Step 16: 20 m north of GAA — within 30 m radius  ← ARRIVE GAA (tour complete)
const S16 = { lat: 52.476182, lon: -8.541206 }; // haversine ≈ 20 m to GAA

// ---------------------------------------------------------------------------
// Distance sanity check (runs at module load — if any distance is wrong the
// test file itself will throw, making the miscalculation obvious)
// ---------------------------------------------------------------------------

function assertApprox(actual, expected, tolerance = 3, label = '') {
  if (Math.abs(actual - expected) > tolerance) {
    throw new Error(
      `Distance sanity check failed ${label}: expected ~${expected}m got ${actual.toFixed(1)}m`
    );
  }
}

assertApprox(haversineDistance(S5.lat, S5.lon, TF.latitude, TF.longitude),  30, 3, 'S5→TF');
assertApprox(haversineDistance(S6.lat, S6.lon, TF.latitude, TF.longitude),  20, 3, 'S6→TF');
assertApprox(haversineDistance(S8.lat, S8.lon, CH.latitude, CH.longitude),  27, 4, 'S8→CH');
assertApprox(haversineDistance(S9.lat, S9.lon, CH.latitude, CH.longitude),  25, 3, 'S9→CH');
assertApprox(haversineDistance(S10.lat, S10.lon, CH.latitude, CH.longitude), 15, 3, 'S10→CH');
assertApprox(haversineDistance(S12.lat, S12.lon, SW.latitude, SW.longitude), 20, 3, 'S12→SW');
assertApprox(haversineDistance(S13.lat, S13.lon, SW.latitude, SW.longitude), 10, 3, 'S13→SW');
assertApprox(haversineDistance(S15.lat, S15.lon, GAA.latitude, GAA.longitude), 35, 3, 'S15→GAA');
assertApprox(haversineDistance(S16.lat, S16.lon, GAA.latitude, GAA.longitude), 20, 3, 'S16→GAA');

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('simulated walk — complete Bruff Heritage Trail', () => {
  let storage;
  let tour;

  beforeEach(() => {
    storage = new MockStorage();
    tour = makeTour(storage);
  });

  // ---- Initial state -------------------------------------------------------

  describe('initial state', () => {
    it('starts at waypoint 0 (Thomas Fitzgerald Centre)', () => {
      expect(tour.getCurrentWaypoint().id).toBe('thomas_fitzgerald_centre');
    });

    it('is not complete', () => {
      expect(tour.isComplete()).toBe(false);
    });
  });

  // ---- Phase 1: Outside boundary → entering Bruff -------------------------

  describe('phase 1: outside-boundary approach from the north', () => {
    it('S1 — 2 km north: is outside boundary', () => {
      expect(isOutsideBoundary(S1.lat, S1.lon)).toBe(true);
    });

    it('S1 — 2 km north: checkProximity returns null', () => {
      expect(step(tour, S1.lat, S1.lon).arrived).toBeNull();
    });

    it('S2 — 1.7 km north: still outside boundary', () => {
      expect(isOutsideBoundary(S2.lat, S2.lon)).toBe(true);
    });

    it('S3 — just inside north boundary edge: is inside boundary', () => {
      expect(isOutsideBoundary(S3.lat, S3.lon)).toBe(false);
    });

    it('S4 — 146 m from TF Centre: inside boundary, no proximity trigger', () => {
      const r = step(tour, S4.lat, S4.lon);
      expect(r.outside).toBe(false);
      expect(r.arrived).toBeNull();
      expect(r.distance).toBeGreaterThan(TF.proximityRadius);
    });
  });

  // ---- Phase 2: Thomas Fitzgerald Centre ----------------------------------

  describe('phase 2: Thomas Fitzgerald Centre', () => {
    it('S5 — 30 m away (outside 25 m radius): no trigger', () => {
      const r = step(tour, S5.lat, S5.lon);
      expect(r.arrived).toBeNull();
      expect(r.distance).toBeGreaterThan(TF.proximityRadius);
    });

    it('S6 — 20 m away (within 25 m radius): checkProximity fires', () => {
      const r = step(tour, S6.lat, S6.lon);
      expect(r.arrived).not.toBeNull();
      expect(r.arrived.id).toBe('thomas_fitzgerald_centre');
      expect(r.distance).toBeLessThanOrEqual(TF.proximityRadius);
    });

    it('S6 → markCurrentVisited advances to Church (index 1)', () => {
      tour.checkProximity(S6.lat, S6.lon);
      tour.markCurrentVisited();
      expect(tour.getCurrentIndex()).toBe(1);
      expect(tour.getCurrentWaypoint().id).toBe('bruff_catholic_church');
    });

    it('thomas_fitzgerald_centre is marked visited after advance', () => {
      tour.checkProximity(S6.lat, S6.lon);
      tour.markCurrentVisited();
      expect(tour.isVisited('thomas_fitzgerald_centre')).toBe(true);
    });

    it('tour is not complete after first visit', () => {
      tour.checkProximity(S6.lat, S6.lon);
      tour.markCurrentVisited();
      expect(tour.isComplete()).toBe(false);
    });
  });

  // ---- Phase 3: Saints Peter & Paul Church --------------------------------

  describe('phase 3: Saints Peter and Paul Church', () => {
    beforeEach(() => {
      // Arrive at and advance past TF Centre
      tour.checkProximity(S6.lat, S6.lon);
      tour.markCurrentVisited();
    });

    it('S7 — duplicate GPS fix at TF spot: no trigger for Church (too far)', () => {
      const d = haversineDistance(S7.lat, S7.lon, CH.latitude, CH.longitude);
      expect(d).toBeGreaterThan(CH.proximityRadius);
      expect(step(tour, S7.lat, S7.lon).arrived).toBeNull();
    });

    it('S8 — 27 m from Church (outside 20 m): no trigger', () => {
      const r = step(tour, S8.lat, S8.lon);
      expect(r.arrived).toBeNull();
      expect(r.distance).toBeGreaterThan(CH.proximityRadius);
    });

    it('S9 — 25 m from Church (outside 20 m): no trigger', () => {
      expect(step(tour, S9.lat, S9.lon).arrived).toBeNull();
    });

    it('S10 — 15 m from Church (within 20 m): checkProximity fires', () => {
      const r = step(tour, S10.lat, S10.lon);
      expect(r.arrived).not.toBeNull();
      expect(r.arrived.id).toBe('bruff_catholic_church');
    });

    it('S10 → markCurrentVisited advances to Sean Wall (index 2)', () => {
      tour.checkProximity(S10.lat, S10.lon);
      tour.markCurrentVisited();
      expect(tour.getCurrentIndex()).toBe(2);
      expect(tour.getCurrentWaypoint().id).toBe('sean_wall_monument');
    });

    it('bruff_catholic_church is marked visited', () => {
      tour.checkProximity(S10.lat, S10.lon);
      tour.markCurrentVisited();
      expect(tour.isVisited('bruff_catholic_church')).toBe(true);
    });
  });

  // ---- Phase 4: Sean Wall Monument ----------------------------------------

  describe('phase 4: Sean Wall Monument', () => {
    beforeEach(() => {
      tour.checkProximity(S6.lat, S6.lon);
      tour.markCurrentVisited();
      tour.checkProximity(S10.lat, S10.lon);
      tour.markCurrentVisited();
    });

    it('S11 — 40 m from SWM (outside 15 m): no trigger', () => {
      expect(step(tour, S11.lat, S11.lon).arrived).toBeNull();
    });

    it('S12 — 20 m from SWM (outside 15 m): no trigger', () => {
      expect(step(tour, S12.lat, S12.lon).arrived).toBeNull();
    });

    it('S13 — 10 m from SWM (within 15 m): checkProximity fires', () => {
      const r = step(tour, S13.lat, S13.lon);
      expect(r.arrived).not.toBeNull();
      expect(r.arrived.id).toBe('sean_wall_monument');
    });

    it('S13 → markCurrentVisited advances to GAA Grounds (index 3)', () => {
      tour.checkProximity(S13.lat, S13.lon);
      tour.markCurrentVisited();
      expect(tour.getCurrentIndex()).toBe(3);
      expect(tour.getCurrentWaypoint().id).toBe('bruff_gaa_grounds');
    });

    it('sean_wall_monument is marked visited', () => {
      tour.checkProximity(S13.lat, S13.lon);
      tour.markCurrentVisited();
      expect(tour.isVisited('sean_wall_monument')).toBe(true);
    });
  });

  // ---- Phase 5: Bruff GAA Grounds → tour complete -------------------------

  describe('phase 5: Bruff GAA Grounds — final waypoint', () => {
    beforeEach(() => {
      tour.checkProximity(S6.lat,  S6.lon);  tour.markCurrentVisited();
      tour.checkProximity(S10.lat, S10.lon); tour.markCurrentVisited();
      tour.checkProximity(S13.lat, S13.lon); tour.markCurrentVisited();
    });

    it('S14 — 253 m from GAA (midpoint walk): no trigger', () => {
      expect(step(tour, S14.lat, S14.lon).arrived).toBeNull();
    });

    it('S15 — 35 m from GAA (outside 30 m): no trigger', () => {
      const r = step(tour, S15.lat, S15.lon);
      expect(r.arrived).toBeNull();
      expect(r.distance).toBeGreaterThan(GAA.proximityRadius);
    });

    it('S16 — 20 m from GAA (within 30 m): checkProximity fires', () => {
      const r = step(tour, S16.lat, S16.lon);
      expect(r.arrived).not.toBeNull();
      expect(r.arrived.id).toBe('bruff_gaa_grounds');
    });

    it('markCurrentVisited on last WP returns true (complete)', () => {
      tour.checkProximity(S16.lat, S16.lon);
      expect(tour.markCurrentVisited()).toBe(true);
    });

    it('isComplete() is true after final mark', () => {
      tour.checkProximity(S16.lat, S16.lon);
      tour.markCurrentVisited();
      expect(tour.isComplete()).toBe(true);
    });

    it('getCurrentWaypoint() is null after completion', () => {
      tour.checkProximity(S16.lat, S16.lon);
      tour.markCurrentVisited();
      expect(tour.getCurrentWaypoint()).toBeNull();
    });

    it('getVisitedIds contains all 4 waypoints in correct order', () => {
      tour.checkProximity(S16.lat, S16.lon);
      tour.markCurrentVisited();
      expect(tour.getVisitedIds()).toEqual([
        'thomas_fitzgerald_centre',
        'bruff_catholic_church',
        'sean_wall_monument',
        'bruff_gaa_grounds',
      ]);
    });

    it('getDistanceToCurrent returns null when complete', () => {
      tour.checkProximity(S16.lat, S16.lon);
      tour.markCurrentVisited();
      expect(tour.getDistanceToCurrent(S16.lat, S16.lon)).toBeNull();
    });
  });

  // ---- Navigation instructions at each stop --------------------------------

  describe('navigation instructions along the route', () => {
    it('from S4 (146 m north), direction to TF Centre is southward', () => {
      const bearing = bearingDeg(S4.lat, S4.lon, TF.latitude, TF.longitude);
      const direction = directionFromBearing(bearing);
      expect(direction).toContain('South');
    });

    it('formatDistance reports < 50 m when within arrival radius', () => {
      const dist = haversineDistance(S6.lat, S6.lon, TF.latitude, TF.longitude);
      expect(formatDistance(dist)).toMatch(/^\d+m$/);
      expect(dist).toBeLessThan(50);
    });

    it('from S14 (midpoint), direction to GAA is southeast (slight south + mostly east)', () => {
      const bearing = bearingDeg(S14.lat, S14.lon, GAA.latitude, GAA.longitude);
      const direction = directionFromBearing(bearing);
      expect(direction).toContain('Southeast');
    });

    it('formatDistance at S4 gives e.g. "150m" (rounded to 10 m)', () => {
      const dist = haversineDistance(S4.lat, S4.lon, TF.latitude, TF.longitude);
      const text = formatDistance(dist);
      expect(text).toMatch(/^\d+0m$/); // ends in 0m — rounded to nearest 10
    });
  });

  // ---- Boundary detection along the route ---------------------------------

  describe('boundary detection along the route', () => {
    const insideSteps  = [S3, S4, S5, S6, S8, S9, S10, S11, S12, S13, S14, S15, S16];
    const outsideSteps = [S1, S2];

    for (const s of outsideSteps) {
      it(`(${s.lat.toFixed(4)}, ${s.lon.toFixed(4)}) is OUTSIDE boundary`, () => {
        expect(isOutsideBoundary(s.lat, s.lon)).toBe(true);
      });
    }

    for (const s of insideSteps) {
      it(`(${s.lat.toFixed(4)}, ${s.lon.toFixed(4)}) is INSIDE boundary`, () => {
        expect(isOutsideBoundary(s.lat, s.lon)).toBe(false);
      });
    }
  });

  // ---- State persistence: second TourController reads same storage ---------

  describe('state persists across page navigation (new TourController instance)', () => {
    it('complete walk state survives re-instantiation', () => {
      // Full walk
      tour.checkProximity(S6.lat,  S6.lon);  tour.markCurrentVisited();
      tour.checkProximity(S10.lat, S10.lon); tour.markCurrentVisited();
      tour.checkProximity(S13.lat, S13.lon); tour.markCurrentVisited();
      tour.checkProximity(S16.lat, S16.lon); tour.markCurrentVisited();

      // Simulate navigating to complete.html → new JS context
      const tour2 = new TourController(WAYPOINTS, storage);
      expect(tour2.isComplete()).toBe(true);
      expect(tour2.getVisitedIds()).toHaveLength(4);
    });

    it('mid-walk state at index 2 is restored correctly', () => {
      tour.checkProximity(S6.lat,  S6.lon);  tour.markCurrentVisited();
      tour.checkProximity(S10.lat, S10.lon); tour.markCurrentVisited();
      // At Sean Wall now

      const tour2 = new TourController(WAYPOINTS, storage);
      expect(tour2.getCurrentIndex()).toBe(2);
      expect(tour2.getCurrentWaypoint().id).toBe('sean_wall_monument');
      expect(tour2.isVisited('thomas_fitzgerald_centre')).toBe(true);
      expect(tour2.isVisited('bruff_catholic_church')).toBe(true);
      expect(tour2.isVisited('sean_wall_monument')).toBe(false);
    });

    it('reset after full walk restores tour to beginning', () => {
      tour.checkProximity(S6.lat,  S6.lon);  tour.markCurrentVisited();
      tour.checkProximity(S10.lat, S10.lon); tour.markCurrentVisited();
      tour.checkProximity(S13.lat, S13.lon); tour.markCurrentVisited();
      tour.checkProximity(S16.lat, S16.lon); tour.markCurrentVisited();

      tour.reset();
      const tour2 = new TourController(WAYPOINTS, storage);
      expect(tour2.isComplete()).toBe(false);
      expect(tour2.getCurrentIndex()).toBe(0);
      expect(tour2.getVisitedIds()).toHaveLength(0);
    });
  });
});
