/**
 * Shared helpers for Playwright E2E tests.
 */

/**
 * Stub map.js so initTourPage() works without a real Leaflet/DOM map.
 * Must be called before page.goto().
 */
export async function stubMapModule(page) {
  await page.route('**/js/map.js', (route) => {
    route.fulfill({
      contentType: 'application/javascript',
      body: `
export function initMap()             { return {}; }
export function destroyMap()          {}
export function addWaypointMarkers()  {}
export function updateUserLocation()  {}
export function drawRoute()           {}
export function panTo()               {}
      `,
    });
  });
}

/**
 * Inject a fake navigator.geolocation before any scripts run.
 * Exposes window.simulatePosition(lat, lon) for tests to fire GPS fixes.
 * Must be called before page.goto().
 */
export async function injectGeoMock(page) {
  await page.addInitScript(() => {
    let _successCb = null;
    const geoMock = {
      watchPosition(success) {
        _successCb = success;
        return 1;
      },
      clearWatch() {
        _successCb = null;
      },
    };
    // navigator.geolocation is a non-writable prototype property in Chromium;
    // define an own property on the navigator instance to shadow it.
    Object.defineProperty(navigator, 'geolocation', {
      get: () => geoMock,
      configurable: true,
    });
    window.simulatePosition = (lat, lon, accuracy = 5) => {
      if (_successCb) {
        _successCb({ coords: { latitude: lat, longitude: lon, accuracy } });
      }
    };
  });
}

/**
 * Seed sessionStorage before the first page load.
 * Must be called before page.goto().
 * Uses a one-shot sentinel so the init script does NOT fire again if the
 * page navigates onward (e.g. detail → complete), which would overwrite
 * the state that the app itself wrote during that navigation.
 */
export async function seedTourState(page, state) {
  await page.addInitScript((s) => {
    if (!sessionStorage.getItem('__bruff_seed_done__')) {
      sessionStorage.setItem('__bruff_seed_done__', '1');
      sessionStorage.setItem('bruff_tour_state', JSON.stringify(s));
    }
  }, state);
}

/** Complete-tour sessionStorage state (all 4 visited). */
export const COMPLETE_STATE = {
  currentIndex: 4,
  visitedIds: [
    'thomas_fitzgerald_centre',
    'bruff_catholic_church',
    'sean_wall_monument',
    'bruff_gaa_grounds',
  ],
  lastNotifiedId: null,
};

/** Mid-walk state: on GAA Grounds (index 3). */
export const AT_GAA_STATE = {
  currentIndex: 3,
  visitedIds: [
    'thomas_fitzgerald_centre',
    'bruff_catholic_church',
    'sean_wall_monument',
  ],
  lastNotifiedId: null,
};
