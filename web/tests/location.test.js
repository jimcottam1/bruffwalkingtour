import { describe, it, expect, vi, beforeEach } from 'vitest';
import { LocationService } from '../js/location.js';

function mockGeo({ watchId = 42 } = {}) {
  return {
    watchPosition: vi.fn().mockReturnValue(watchId),
    clearWatch: vi.fn(),
  };
}

describe('LocationService', () => {
  it('creates without throwing when geolocation is injected', () => {
    expect(() => new LocationService(mockGeo())).not.toThrow();
  });

  it('isActive() returns false before start()', () => {
    expect(new LocationService(mockGeo()).isActive()).toBe(false);
  });

  it('start() calls watchPosition', () => {
    const geo = mockGeo();
    const svc = new LocationService(geo);
    svc.start(() => {}, () => {});
    expect(geo.watchPosition).toHaveBeenCalledTimes(1);
  });

  it('start() sets enableHighAccuracy: true', () => {
    const geo = mockGeo();
    const svc = new LocationService(geo);
    svc.start(() => {}, () => {});
    const [, , options] = geo.watchPosition.mock.calls[0];
    expect(options.enableHighAccuracy).toBe(true);
  });

  it('isActive() returns true after start()', () => {
    const geo = mockGeo();
    const svc = new LocationService(geo);
    svc.start(() => {}, () => {});
    expect(svc.isActive()).toBe(true);
  });

  it('stop() calls clearWatch with the correct watch ID', () => {
    const geo = mockGeo({ watchId: 7 });
    const svc = new LocationService(geo);
    svc.start(() => {}, () => {});
    svc.stop();
    expect(geo.clearWatch).toHaveBeenCalledWith(7);
  });

  it('isActive() returns false after stop()', () => {
    const geo = mockGeo();
    const svc = new LocationService(geo);
    svc.start(() => {}, () => {});
    svc.stop();
    expect(svc.isActive()).toBe(false);
  });

  it('stop() does nothing if not started', () => {
    const geo = mockGeo();
    const svc = new LocationService(geo);
    expect(() => svc.stop()).not.toThrow();
    expect(geo.clearWatch).not.toHaveBeenCalled();
  });

  it('onUpdate is called with { lat, lon, accuracy }', () => {
    const geo = mockGeo();
    const svc = new LocationService(geo);
    const onUpdate = vi.fn();
    svc.start(onUpdate, () => {});

    const successCb = geo.watchPosition.mock.calls[0][0];
    successCb({ coords: { latitude: 52.477, longitude: -8.548, accuracy: 5 } });

    expect(onUpdate).toHaveBeenCalledWith({ lat: 52.477, lon: -8.548, accuracy: 5 });
  });

  it('onError is called with a string message on code 1 (permission denied)', () => {
    const geo = mockGeo();
    const svc = new LocationService(geo);
    const onError = vi.fn();
    svc.start(() => {}, onError);

    const errorCb = geo.watchPosition.mock.calls[0][1];
    errorCb({ code: 1 });

    expect(onError).toHaveBeenCalledWith(expect.stringMatching(/permission/i));
  });

  it('onError is called with a string message on code 2 (unavailable)', () => {
    const geo = mockGeo();
    const svc = new LocationService(geo);
    const onError = vi.fn();
    svc.start(() => {}, onError);

    const errorCb = geo.watchPosition.mock.calls[0][1];
    errorCb({ code: 2 });

    expect(onError).toHaveBeenCalledWith(expect.stringMatching(/unavailable/i));
  });

  it('onError is called with a string message on code 3 (timeout)', () => {
    const geo = mockGeo();
    const svc = new LocationService(geo);
    const onError = vi.fn();
    svc.start(() => {}, onError);

    const errorCb = geo.watchPosition.mock.calls[0][1];
    errorCb({ code: 3 });

    expect(onError).toHaveBeenCalledWith(expect.stringMatching(/timed? ?out/i));
  });

  it('onError is called when geolocation is null', () => {
    const svc = new LocationService(null);
    const onError = vi.fn();
    svc.start(() => {}, onError);
    expect(onError).toHaveBeenCalledWith(expect.any(String));
  });

  it('isActive() remains false when geolocation is null', () => {
    const svc = new LocationService(null);
    svc.start(() => {}, () => {});
    expect(svc.isActive()).toBe(false);
  });
});

describe('LocationService.isSupported', () => {
  it('returns a boolean', () => {
    expect(typeof LocationService.isSupported()).toBe('boolean');
  });
});
