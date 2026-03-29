import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { fallbackRoute, fetchRoute, getRoute } from '../js/routing.js';

// ---- fallbackRoute (pure, no mocking needed) --------------------------------

describe('fallbackRoute', () => {
  it('direct route for very short distances (< 0.0005 deg)', () => {
    const route = fallbackRoute(52.477, -8.548, 52.4771, -8.5481);
    expect(route).toHaveLength(2);
    expect(route[0]).toEqual([52.477, -8.548]);
    expect(route[1]).toEqual([52.4771, -8.5481]);
  });

  it('L-shaped route for primarily north-south movement', () => {
    const route = fallbackRoute(52.477, -8.548, 52.490, -8.549);
    expect(route).toHaveLength(4);
    expect(route[0]).toEqual([52.477, -8.548]);
    expect(route[route.length - 1]).toEqual([52.490, -8.549]);
  });

  it('L-shaped route for primarily east-west movement', () => {
    const route = fallbackRoute(52.477, -8.548, 52.478, -8.540);
    expect(route).toHaveLength(4);
    expect(route[0]).toEqual([52.477, -8.548]);
    expect(route[route.length - 1]).toEqual([52.478, -8.540]);
  });

  it('always starts at from-coordinate', () => {
    const route = fallbackRoute(52.477636, -8.547905, 52.476002, -8.541206);
    expect(route[0][0]).toBeCloseTo(52.477636, 5);
    expect(route[0][1]).toBeCloseTo(-8.547905, 5);
  });

  it('always ends at to-coordinate', () => {
    const route = fallbackRoute(52.477636, -8.547905, 52.476002, -8.541206);
    const last = route[route.length - 1];
    expect(last[0]).toBeCloseTo(52.476002, 5);
    expect(last[1]).toBeCloseTo(-8.541206, 5);
  });

  it('returns at least 2 points', () => {
    const route = fallbackRoute(52.477, -8.548, 52.476, -8.541);
    expect(route.length).toBeGreaterThanOrEqual(2);
  });

  it('all points are [lat, lon] pairs', () => {
    const route = fallbackRoute(52.477, -8.548, 52.476, -8.541);
    for (const pt of route) {
      expect(pt).toHaveLength(2);
      expect(pt[0]).toBeGreaterThan(50); // lat is ~52
      expect(pt[1]).toBeLessThan(0);     // lon is negative (West)
    }
  });
});

// ---- fetchRoute (mocked fetch) ---------------------------------------------

describe('fetchRoute', () => {
  beforeEach(() => vi.stubGlobal('fetch', vi.fn()));
  afterEach(() => vi.unstubAllGlobals());

  it('flips OSRM [lon, lat] to [lat, lon] for Leaflet', async () => {
    fetch.mockResolvedValueOnce({
      ok: true,
      json: () =>
        Promise.resolve({
          routes: [
            {
              geometry: {
                coordinates: [
                  [-8.548776, 52.478689],
                  [-8.547905, 52.477636],
                ],
              },
            },
          ],
        }),
    });

    const route = await fetchRoute(52.478689, -8.548776, 52.477636, -8.547905);
    expect(route[0]).toEqual([52.478689, -8.548776]);
    expect(route[1]).toEqual([52.477636, -8.547905]);
  });

  it('throws when the HTTP response is not OK', async () => {
    fetch.mockResolvedValueOnce({ ok: false, status: 500 });
    await expect(fetchRoute(52.477, -8.548, 52.476, -8.541)).rejects.toThrow('500');
  });

  it('throws when OSRM returns an empty routes array', async () => {
    fetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ routes: [] }),
    });
    await expect(fetchRoute(52.477, -8.548, 52.476, -8.541)).rejects.toThrow('no routes');
  });

  it('throws on network error', async () => {
    fetch.mockRejectedValueOnce(new Error('Network error'));
    await expect(fetchRoute(52.477, -8.548, 52.476, -8.541)).rejects.toThrow();
  });

  it('builds OSRM URL with lon,lat;lon,lat coordinate order', async () => {
    fetch.mockResolvedValueOnce({
      ok: true,
      json: () =>
        Promise.resolve({
          routes: [{ geometry: { coordinates: [[-8.548, 52.477]] } }],
        }),
    });

    await fetchRoute(52.477, -8.548, 52.476, -8.541).catch(() => {});
    const calledUrl = fetch.mock.calls[0][0];
    // OSRM expects lon,lat — so -8.548,52.477;-8.541,52.476
    expect(calledUrl).toContain('-8.548,52.477;-8.541,52.476');
  });
});

// ---- getRoute (mocked fetch) -----------------------------------------------

describe('getRoute', () => {
  beforeEach(() => vi.stubGlobal('fetch', vi.fn()));
  afterEach(() => vi.unstubAllGlobals());

  it('returns the OSRM route when fetch succeeds', async () => {
    fetch.mockResolvedValueOnce({
      ok: true,
      json: () =>
        Promise.resolve({
          routes: [
            {
              geometry: {
                coordinates: [[-8.548776, 52.478689], [-8.547905, 52.477636]],
              },
            },
          ],
        }),
    });

    const route = await getRoute(52.478689, -8.548776, 52.477636, -8.547905);
    expect(route).toHaveLength(2);
    expect(route[0]).toEqual([52.478689, -8.548776]);
  });

  it('falls back to fallbackRoute when fetch throws', async () => {
    fetch.mockRejectedValueOnce(new Error('offline'));
    const route = await getRoute(52.477, -8.548, 52.476, -8.541);
    expect(route.length).toBeGreaterThanOrEqual(2);
    expect(route[0][0]).toBeCloseTo(52.477, 3);
    expect(route[route.length - 1][0]).toBeCloseTo(52.476, 3);
  });

  it('falls back to fallbackRoute when HTTP error is returned', async () => {
    fetch.mockResolvedValueOnce({ ok: false, status: 503 });
    const route = await getRoute(52.477, -8.548, 52.476, -8.541);
    expect(route.length).toBeGreaterThanOrEqual(2);
  });

  it('never throws — always resolves with an array', async () => {
    fetch.mockRejectedValueOnce(new Error('any error'));
    await expect(getRoute(52.477, -8.548, 52.476, -8.541)).resolves.toBeInstanceOf(Array);
  });
});
