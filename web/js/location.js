/**
 * LocationService wraps the browser Geolocation API.
 *
 * IMPORTANT: watchPosition requires HTTPS on iOS Safari and will pause when
 * the app is backgrounded. Always serve this PWA over HTTPS in production.
 */

export class LocationService {
  /**
   * @param {Geolocation|null} geo - injectable for testing; defaults to navigator.geolocation
   */
  constructor(geo = null) {
    this._geo =
      geo ?? (typeof navigator !== 'undefined' ? navigator.geolocation : null);
    this._watchId = null;
  }

  /** Returns true if the Geolocation API is available in this browser. */
  static isSupported() {
    return typeof navigator !== 'undefined' && 'geolocation' in navigator;
  }

  /**
   * Start watching the user's position.
   * @param {function} onUpdate - called with { lat, lon, accuracy } on each fix
   * @param {function} onError  - called with a human-readable error string
   */
  start(onUpdate, onError) {
    if (!this._geo) {
      onError?.('Geolocation is not supported by this browser.');
      return;
    }
    this._watchId = this._geo.watchPosition(
      (pos) => {
        onUpdate({
          lat: pos.coords.latitude,
          lon: pos.coords.longitude,
          accuracy: pos.coords.accuracy,
        });
      },
      (err) => {
        const messages = {
          1: 'Location permission denied. Please allow location access to use the tour.',
          2: 'Location unavailable. Please check your device settings.',
          3: 'Location request timed out.',
        };
        onError?.(messages[err.code] ?? 'An unknown location error occurred.');
      },
      {
        enableHighAccuracy: true, // Matches Android PRIORITY_HIGH_ACCURACY
        maximumAge: 3000,         // Accept fixes up to 3 s old (matches Android interval)
        timeout: 10000,
      },
    );
  }

  /** Stop watching and release resources. Safe to call if not started. */
  stop() {
    if (this._geo && this._watchId !== null) {
      this._geo.clearWatch(this._watchId);
      this._watchId = null;
    }
  }

  isActive() {
    return this._watchId !== null;
  }
}
