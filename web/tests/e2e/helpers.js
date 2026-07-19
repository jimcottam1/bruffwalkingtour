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
export function recentre()            {}
export function isFollowing()         { return true; }
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
    // The app registers its watchPosition callback asynchronously (after its
    // ES module chain finishes loading), so a fix fired immediately after a
    // client-side navigation (e.g. clicking back to tour.html) can arrive
    // before the callback is registered. Poll briefly instead of dropping it,
    // mirroring how a real GPS provider keeps emitting fixes until the app's
    // watcher is ready.
    window.simulatePosition = (lat, lon, accuracy = 5) => {
      const fire = () => _successCb({ coords: { latitude: lat, longitude: lon, accuracy } });
      if (_successCb) {
        fire();
        return;
      }
      const deadline = Date.now() + 5000;
      const poll = () => {
        if (_successCb) {
          fire();
        } else if (Date.now() < deadline) {
          setTimeout(poll, 20);
        }
      };
      poll();
    };
  });
}

/**
 * Fire a GPS fix inside the tour boundary and dismiss the boundary gate by
 * clicking Start Tour, landing the page in "live tour" mode. Must be called
 * after page.goto('/tour.html').
 */
export async function startTourAt(page, coords) {
  await page.evaluate(({ lat, lon }) => window.simulatePosition(lat, lon), coords);
  await page.locator('#start-tour-btn').click();
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
