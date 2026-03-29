import { describe, it, expect } from 'vitest';
import {
  haversineDistance,
  bearingDeg,
  directionFromBearing,
  formatDistance,
  formatEta,
  isOutsideBoundary,
} from '../js/utils.js';
import { BOUNDARY } from '../js/data.js';

// ---- haversineDistance -----------------------------------------------------

describe('haversineDistance', () => {
  it('returns ~0 for identical coordinates', () => {
    expect(haversineDistance(52.477636, -8.547905, 52.477636, -8.547905)).toBeLessThan(0.01);
  });

  it('measures Thomas Fitzgerald Centre → Sean Wall Monument (~150 m)', () => {
    const d = haversineDistance(52.478689, -8.548776, 52.477636, -8.547905);
    expect(d).toBeGreaterThan(100);
    expect(d).toBeLessThan(200);
  });

  it('measures Bruff → Limerick city (> 20 km)', () => {
    const d = haversineDistance(52.4773, -8.548, 52.6638, -8.6267);
    expect(d).toBeGreaterThan(20_000);
    expect(d).toBeLessThan(30_000);
  });

  it('is symmetric', () => {
    const d1 = haversineDistance(52.478689, -8.548776, 52.477636, -8.547905);
    const d2 = haversineDistance(52.477636, -8.547905, 52.478689, -8.548776);
    expect(Math.abs(d1 - d2)).toBeLessThan(0.001);
  });

  it('Sean Wall → GAA Grounds is ~ 600 m', () => {
    const d = haversineDistance(52.477636, -8.547905, 52.476002, -8.541206);
    expect(d).toBeGreaterThan(450);
    expect(d).toBeLessThan(750);
  });
});

// ---- bearingDeg ------------------------------------------------------------

describe('bearingDeg', () => {
  it('heading due north gives ~0° (or ~360 — both are valid)', () => {
    const b = bearingDeg(52.47, -8.54, 52.48, -8.54);
    expect(b < 2 || b >= 358).toBe(true);
  });

  it('heading due east gives ~90°', () => {
    const b = bearingDeg(52.47, -8.55, 52.47, -8.54);
    expect(b).toBeGreaterThan(85);
    expect(b).toBeLessThan(95);
  });

  it('heading due south gives ~180°', () => {
    const b = bearingDeg(52.48, -8.54, 52.47, -8.54);
    expect(b).toBeGreaterThan(175);
    expect(b).toBeLessThan(185);
  });

  it('heading due west gives ~270°', () => {
    const b = bearingDeg(52.47, -8.54, 52.47, -8.55);
    expect(b).toBeGreaterThan(265);
    expect(b).toBeLessThan(275);
  });

  it('always returns a value in [0, 360)', () => {
    const b = bearingDeg(52.47, -8.54, 52.46, -8.55);
    expect(b).toBeGreaterThanOrEqual(0);
    expect(b).toBeLessThan(360);
  });
});

// ---- directionFromBearing --------------------------------------------------

describe('directionFromBearing', () => {
  it('0° → Head North', () => expect(directionFromBearing(0)).toBe('Head North'));
  it('359° → Head North', () => expect(directionFromBearing(359)).toBe('Head North'));
  it('22.4° → Head North (boundary)', () => expect(directionFromBearing(22.4)).toBe('Head North'));
  it('22.5° → Head Northeast', () => expect(directionFromBearing(22.5)).toBe('Head Northeast'));
  it('45° → Head Northeast', () => expect(directionFromBearing(45)).toBe('Head Northeast'));
  it('90° → Head East', () => expect(directionFromBearing(90)).toBe('Head East'));
  it('135° → Head Southeast', () => expect(directionFromBearing(135)).toBe('Head Southeast'));
  it('180° → Head South', () => expect(directionFromBearing(180)).toBe('Head South'));
  it('225° → Head Southwest', () => expect(directionFromBearing(225)).toBe('Head Southwest'));
  it('270° → Head West', () => expect(directionFromBearing(270)).toBe('Head West'));
  it('315° → Head Northwest', () => expect(directionFromBearing(315)).toBe('Head Northwest'));
  it('337.5° → Head North (wrap boundary)', () => expect(directionFromBearing(337.5)).toBe('Head North'));
  it('337.4° → Head Northwest (just below wrap)', () => expect(directionFromBearing(337.4)).toBe('Head Northwest'));
  it('negative bearing is normalised (-10° → Head North)', () => expect(directionFromBearing(-10)).toBe('Head North'));
  it('bearing > 360 is normalised (370° mod 360 = 10° → Head North)', () => expect(directionFromBearing(370)).toBe('Head North'));
  it('bearing > 360 is normalised (405° mod 360 = 45° → Head Northeast)', () => expect(directionFromBearing(405)).toBe('Head Northeast'));
});

// ---- formatDistance --------------------------------------------------------

describe('formatDistance', () => {
  it('< 50 m: exact metres', () => {
    expect(formatDistance(1)).toBe('1m');
    expect(formatDistance(23)).toBe('23m');
    expect(formatDistance(49)).toBe('49m');
  });

  it('50–999 m: rounded to nearest 10 m', () => {
    expect(formatDistance(50)).toBe('50m');
    expect(formatDistance(55)).toBe('60m');
    expect(formatDistance(133)).toBe('130m');
    expect(formatDistance(137)).toBe('140m');
    expect(formatDistance(995)).toBe('1000m');
  });

  it('>= 1000 m: X.X km', () => {
    expect(formatDistance(1000)).toBe('1.0km');
    expect(formatDistance(1500)).toBe('1.5km');
    expect(formatDistance(2345)).toBe('2.3km');
    expect(formatDistance(10_000)).toBe('10.0km');
  });
});

// ---- formatEta -------------------------------------------------------------

describe('formatEta', () => {
  it('very short distance → 1 min (minimum)', () => {
    expect(formatEta(10)).toBe('1 min');
  });

  it('exactly 830 m → 10 min at 83 m/min', () => {
    expect(formatEta(830)).toBe('10 min');
  });

  it('rounds up partial minutes', () => {
    expect(formatEta(84)).toBe('2 min');   // 84/83 = 1.01 → ceil = 2
    expect(formatEta(83)).toBe('1 min');   // exactly 1 min
  });
});

// ---- isOutsideBoundary -----------------------------------------------------

describe('isOutsideBoundary', () => {
  it('returns false for the tour centre (Sean Wall Monument coords)', () => {
    expect(isOutsideBoundary(BOUNDARY.CENTER_LAT, BOUNDARY.CENTER_LON)).toBe(false);
  });

  it('returns false for all 4 waypoints', () => {
    expect(isOutsideBoundary(52.478689, -8.548776)).toBe(false); // Thomas Fitzgerald
    expect(isOutsideBoundary(52.478558, -8.548009)).toBe(false); // Church
    expect(isOutsideBoundary(52.477636, -8.547905)).toBe(false); // Sean Wall
    expect(isOutsideBoundary(52.476002, -8.541206)).toBe(false); // GAA
  });

  it('returns true for Limerick city centre', () => {
    expect(isOutsideBoundary(52.6638, -8.6267)).toBe(true);
  });

  it('returns true for a point 2 km north of centre', () => {
    const northLat = BOUNDARY.CENTER_LAT + 0.02; // ~2.2 km north
    expect(isOutsideBoundary(northLat, BOUNDARY.CENTER_LON)).toBe(true);
  });

  it('returns true for a point 2 km east of centre', () => {
    const eastLon = BOUNDARY.CENTER_LON + 0.02;
    expect(isOutsideBoundary(BOUNDARY.CENTER_LAT, eastLon)).toBe(true);
  });

  it('accepts a custom boundary object', () => {
    const tiny = { CENTER_LAT: 52.477, CENTER_LON: -8.548, WIDTH_KM: 0.1, HEIGHT_KM: 0.1 };
    expect(isOutsideBoundary(52.480, -8.548, tiny)).toBe(true);  // too far north
    expect(isOutsideBoundary(52.477, -8.548, tiny)).toBe(false); // at centre
  });
});
