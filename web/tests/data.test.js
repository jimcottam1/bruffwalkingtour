import { describe, it, expect } from 'vitest';
import {
  WAYPOINTS,
  TOUR,
  BOUNDARY,
  WALKING_SPEED_MPM,
  getWaypointById,
  getDefaultTour,
} from '../js/data.js';

describe('WAYPOINTS', () => {
  it('has exactly 4 waypoints', () => {
    expect(WAYPOINTS).toHaveLength(4);
  });

  it('all waypoints have required fields', () => {
    const required = ['id', 'name', 'description', 'historicalInfo', 'latitude', 'longitude', 'proximityRadius'];
    for (const wp of WAYPOINTS) {
      for (const field of required) {
        expect(wp, `${wp.id} is missing ${field}`).toHaveProperty(field);
      }
    }
  });

  it('all waypoints have valid Bruff-area coordinates', () => {
    for (const wp of WAYPOINTS) {
      expect(wp.latitude, `${wp.id} latitude`).toBeGreaterThan(52.47);
      expect(wp.latitude, `${wp.id} latitude`).toBeLessThan(52.49);
      expect(wp.longitude, `${wp.id} longitude`).toBeGreaterThan(-8.56);
      expect(wp.longitude, `${wp.id} longitude`).toBeLessThan(-8.53);
    }
  });

  it('all waypoint IDs are unique', () => {
    const ids = WAYPOINTS.map((wp) => wp.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('all proximity radii are positive numbers', () => {
    for (const wp of WAYPOINTS) {
      expect(typeof wp.proximityRadius).toBe('number');
      expect(wp.proximityRadius).toBeGreaterThan(0);
    }
  });

  it('waypoints are in the correct tour order', () => {
    expect(WAYPOINTS[0].id).toBe('thomas_fitzgerald_centre');
    expect(WAYPOINTS[1].id).toBe('bruff_catholic_church');
    expect(WAYPOINTS[2].id).toBe('sean_wall_monument');
    expect(WAYPOINTS[3].id).toBe('bruff_gaa_grounds');
  });

  it('Thomas Fitzgerald Centre has a local image path', () => {
    const wp = WAYPOINTS.find((w) => w.id === 'thomas_fitzgerald_centre');
    expect(wp.localImage).toBeTruthy();
  });
});

describe('getWaypointById', () => {
  it('returns the correct waypoint', () => {
    const wp = getWaypointById('sean_wall_monument');
    expect(wp).not.toBeNull();
    expect(wp.name).toBe('Sean Wall Monument');
  });

  it('returns null for an unknown ID', () => {
    expect(getWaypointById('nonexistent')).toBeNull();
  });

  it('returns null for an empty string', () => {
    expect(getWaypointById('')).toBeNull();
  });

  it('returns null for null input', () => {
    expect(getWaypointById(null)).toBeNull();
  });
});

describe('BOUNDARY', () => {
  it('matches the constants from Android LocationService.kt', () => {
    expect(BOUNDARY.CENTER_LAT).toBeCloseTo(52.47785299293757, 10);
    expect(BOUNDARY.CENTER_LON).toBeCloseTo(-8.54801677334652, 10);
    expect(BOUNDARY.WIDTH_KM).toBe(1.5);
    expect(BOUNDARY.HEIGHT_KM).toBe(3.0);
  });
});

describe('TOUR metadata', () => {
  it('has 90 minutes estimated duration', () => {
    expect(TOUR.estimatedDurationMinutes).toBe(90);
  });

  it('is EASY difficulty', () => {
    expect(TOUR.difficulty).toBe('EASY');
  });

  it('has an id and name', () => {
    expect(TOUR.id).toBeTruthy();
    expect(TOUR.name).toBeTruthy();
  });
});

describe('WALKING_SPEED_MPM', () => {
  it('is 83 metres per minute (5 km/h)', () => {
    expect(WALKING_SPEED_MPM).toBe(83);
  });
});

describe('getDefaultTour', () => {
  it('includes waypoints in the returned tour object', () => {
    const tour = getDefaultTour();
    expect(tour.waypoints).toHaveLength(4);
  });

  it('returns a new object each call (not shared reference)', () => {
    expect(getDefaultTour()).not.toBe(getDefaultTour());
  });

  it('waypoints reference the same objects as WAYPOINTS', () => {
    expect(getDefaultTour().waypoints[0]).toBe(WAYPOINTS[0]);
  });
});
