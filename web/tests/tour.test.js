import { describe, it, expect, beforeEach } from 'vitest';
import { TourController } from '../js/tour.js';
import { WAYPOINTS } from '../js/data.js';

class MockStorage {
  constructor() { this._data = {}; }
  getItem(k) { return this._data[k] ?? null; }
  setItem(k, v) { this._data[k] = String(v); }
  removeItem(k) { delete this._data[k]; }
  clear() { this._data = {}; }
}

let storage;
let tour;

beforeEach(() => {
  storage = new MockStorage();
  tour = new TourController(WAYPOINTS, storage);
});

// ---- Initial state ---------------------------------------------------------

describe('initial state', () => {
  it('starts at index 0', () => {
    expect(tour.getCurrentIndex()).toBe(0);
  });

  it('first waypoint is Thomas Fitzgerald Centre', () => {
    expect(tour.getCurrentWaypoint().id).toBe('thomas_fitzgerald_centre');
  });

  it('is not complete', () => {
    expect(tour.isComplete()).toBe(false);
  });

  it('has 4 waypoints', () => {
    expect(tour.getWaypointCount()).toBe(4);
  });

  it('no waypoints are visited', () => {
    for (const wp of WAYPOINTS) {
      expect(tour.isVisited(wp.id)).toBe(false);
    }
  });

  it('getVisitedIds returns empty array', () => {
    expect(tour.getVisitedIds()).toEqual([]);
  });
});

// ---- markCurrentVisited ----------------------------------------------------

describe('markCurrentVisited', () => {
  it('advances to index 1', () => {
    tour.markCurrentVisited();
    expect(tour.getCurrentIndex()).toBe(1);
  });

  it('marks the waypoint as visited', () => {
    tour.markCurrentVisited();
    expect(tour.isVisited('thomas_fitzgerald_centre')).toBe(true);
  });

  it('is not complete after one advance', () => {
    tour.markCurrentVisited();
    expect(tour.isComplete()).toBe(false);
  });

  it('completing all 4 waypoints sets isComplete() to true', () => {
    tour.markCurrentVisited(); // 0→1
    tour.markCurrentVisited(); // 1→2
    tour.markCurrentVisited(); // 2→3
    expect(tour.isComplete()).toBe(false);
    tour.markCurrentVisited(); // 3→4  (complete)
    expect(tour.isComplete()).toBe(true);
  });

  it('returns true when tour becomes complete', () => {
    tour.markCurrentVisited();
    tour.markCurrentVisited();
    tour.markCurrentVisited();
    expect(tour.markCurrentVisited()).toBe(true);
  });

  it('returns false before tour is complete', () => {
    expect(tour.markCurrentVisited()).toBe(false);
  });

  it('is idempotent when already complete — does not advance past length', () => {
    tour.markCurrentVisited();
    tour.markCurrentVisited();
    tour.markCurrentVisited();
    tour.markCurrentVisited(); // complete, index = 4
    tour.markCurrentVisited(); // extra call — should not advance to 5
    expect(tour.getCurrentIndex()).toBe(4);
    expect(tour.isComplete()).toBe(true);
  });

  it('getCurrentWaypoint returns null when complete', () => {
    tour.markCurrentVisited();
    tour.markCurrentVisited();
    tour.markCurrentVisited();
    tour.markCurrentVisited();
    expect(tour.getCurrentWaypoint()).toBeNull();
  });
});

// ---- checkProximity --------------------------------------------------------

describe('checkProximity', () => {
  it('returns waypoint when exactly on its coordinates', () => {
    const wp = WAYPOINTS[0];
    const result = tour.checkProximity(wp.latitude, wp.longitude);
    expect(result).not.toBeNull();
    expect(result.id).toBe('thomas_fitzgerald_centre');
  });

  it('returns waypoint when within proximity radius', () => {
    const wp = WAYPOINTS[0]; // radius 25 m
    // ~10 m north
    const result = tour.checkProximity(wp.latitude + 0.00009, wp.longitude);
    expect(result).not.toBeNull();
  });

  it('returns null when outside proximity radius', () => {
    // ~500 m away
    const result = tour.checkProximity(52.482, -8.548);
    expect(result).toBeNull();
  });

  it('only notifies once per waypoint (idempotent)', () => {
    const wp = WAYPOINTS[0];
    const first = tour.checkProximity(wp.latitude, wp.longitude);
    const second = tour.checkProximity(wp.latitude, wp.longitude);
    expect(first).not.toBeNull();
    expect(second).toBeNull();
  });

  it('returns null when tour is complete', () => {
    tour.markCurrentVisited();
    tour.markCurrentVisited();
    tour.markCurrentVisited();
    tour.markCurrentVisited();
    const wp = WAYPOINTS[3];
    expect(tour.checkProximity(wp.latitude, wp.longitude)).toBeNull();
  });

  it('only checks the current waypoint, not previous ones', () => {
    tour.markCurrentVisited(); // advance to waypoint 1 (church)
    const fitzgerald = WAYPOINTS[0];
    // Standing at waypoint 0 but current is waypoint 1 — should not trigger
    expect(tour.checkProximity(fitzgerald.latitude, fitzgerald.longitude)).toBeNull();
  });

  it('can notify for the new waypoint after advancing', () => {
    const wp0 = WAYPOINTS[0];
    tour.checkProximity(wp0.latitude, wp0.longitude); // fires for wp0
    tour.markCurrentVisited(); // advance to wp1
    const wp1 = WAYPOINTS[1];
    const result = tour.checkProximity(wp1.latitude, wp1.longitude);
    expect(result).not.toBeNull();
    expect(result.id).toBe('bruff_catholic_church');
  });
});

// ---- reset -----------------------------------------------------------------

describe('reset', () => {
  it('resets index to 0', () => {
    tour.markCurrentVisited();
    tour.markCurrentVisited();
    tour.reset();
    expect(tour.getCurrentIndex()).toBe(0);
  });

  it('clears visited waypoints', () => {
    tour.markCurrentVisited();
    tour.reset();
    expect(tour.isVisited('thomas_fitzgerald_centre')).toBe(false);
    expect(tour.getVisitedIds()).toEqual([]);
  });

  it('clears notification state so proximity fires again after reset', () => {
    const wp = WAYPOINTS[0];
    tour.checkProximity(wp.latitude, wp.longitude); // fires once
    tour.reset();
    const result = tour.checkProximity(wp.latitude, wp.longitude);
    expect(result).not.toBeNull(); // should fire again after reset
  });

  it('tour is no longer complete after reset', () => {
    tour.markCurrentVisited();
    tour.markCurrentVisited();
    tour.markCurrentVisited();
    tour.markCurrentVisited(); // complete
    tour.reset();
    expect(tour.isComplete()).toBe(false);
  });
});

// ---- State persistence across instances ------------------------------------

describe('state persistence', () => {
  it('restores index and visited IDs on new instance using same storage', () => {
    tour.markCurrentVisited(); // → index 1
    tour.markCurrentVisited(); // → index 2

    const tour2 = new TourController(WAYPOINTS, storage);
    expect(tour2.getCurrentIndex()).toBe(2);
    expect(tour2.isVisited('thomas_fitzgerald_centre')).toBe(true);
    expect(tour2.isVisited('bruff_catholic_church')).toBe(true);
    expect(tour2.isVisited('sean_wall_monument')).toBe(false);
  });

  it('starts fresh when storage is empty', () => {
    const tour2 = new TourController(WAYPOINTS, new MockStorage());
    expect(tour2.getCurrentIndex()).toBe(0);
    expect(tour2.getVisitedIds()).toEqual([]);
  });

  it('starts fresh when storage contains corrupt JSON', () => {
    const badStorage = new MockStorage();
    badStorage.setItem('bruff_tour_state', '{not valid json}');
    const tour2 = new TourController(WAYPOINTS, badStorage);
    expect(tour2.getCurrentIndex()).toBe(0);
  });
});

// ---- getDistanceToCurrent --------------------------------------------------

describe('getDistanceToCurrent', () => {
  it('returns a positive number', () => {
    const d = tour.getDistanceToCurrent(52.482, -8.545);
    expect(d).toBeGreaterThan(0);
  });

  it('returns null when tour is complete', () => {
    tour.markCurrentVisited();
    tour.markCurrentVisited();
    tour.markCurrentVisited();
    tour.markCurrentVisited();
    expect(tour.getDistanceToCurrent(52.477, -8.548)).toBeNull();
  });

  it('returns ~0 when standing on the current waypoint', () => {
    const wp = WAYPOINTS[0];
    const d = tour.getDistanceToCurrent(wp.latitude, wp.longitude);
    expect(d).toBeLessThan(1);
  });
});
