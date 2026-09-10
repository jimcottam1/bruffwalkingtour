/**
 * Page initializers — one exported function per HTML page.
 * This is the only module that touches the DOM directly (apart from map.js).
 */

import { WAYPOINTS, BOUNDARY, getWaypointById } from './data.js';
import {
  haversineDistance,
  bearingDeg,
  directionFromBearing,
  formatDistance,
  formatEta,
  isOutsideBoundary,
} from './utils.js';
import { TourController } from './tour.js';
import { LocationService } from './location.js';
import {
  initMap,
  addWaypointMarkers,
  updateUserLocation,
  panTo,
  recentre,
  isFollowing,
} from './map.js';

// GPS fixes with accuracy worse than this are ignored (matching Android threshold)
const ACCURACY_THRESHOLD_M = 50;

// Marks that the boundary gate has already been passed once this tour, so
// returning to tour.html between waypoints (detail.html → tour.html) doesn't
// re-gate the user on every single stop. Cleared on restart (index.html's
// Start button, complete.html's restart button).
const GATE_PASSED_KEY = 'bruff_gate_passed';

// How long the "outside Bruff" gate message stays up before automatically
// returning to the intro screen, rather than leaving the user stuck on it.
const GATE_OUTSIDE_RETURN_DELAY_MS = 6000;

// ---------------------------------------------------------------------------
// Tour page  (tour.html)
// ---------------------------------------------------------------------------

export function initTourPage() {
  const tour = new TourController(WAYPOINTS);

  // If somehow already complete, skip straight to the completion page
  if (tour.isComplete()) {
    window.location.replace('complete.html');
    return;
  }

  const navText       = document.getElementById('navigation-instruction');
  const distText      = document.getElementById('distance-info');
  const boundaryWarn  = document.getElementById('boundary-warning');
  const arrivalBanner = document.getElementById('arrival-banner');
  const arrivalLink   = document.getElementById('arrival-link');
  const recenterBtn   = document.getElementById('recenter-btn');
  const gpsBadge      = document.getElementById('gps-badge');
  const gate          = document.getElementById('boundary-gate');
  const gateStatus    = document.getElementById('gate-status');
  const gateDistance  = document.getElementById('gate-distance');
  const startBtn      = document.getElementById('start-tour-btn');

  // The map/route/navigation stay dormant — nothing is drawn or fetched from
  // OSRM — until the user confirms via the gate that they're inside the tour
  // area and taps Start. This avoids showing a nonsensical hours-long walking
  // ETA (or burning OSRM's free API quota) for someone opening the app from
  // outside Bruff.
  let tourStarted = false;
  let returnToIntroTimer = null;

  // Wire up the re-centre button
  if (recenterBtn) {
    recenterBtn.addEventListener('click', () => {
      recentre();
      recenterBtn.hidden = true;
    });
  }

  function startTour(lastFix) {
    if (tourStarted) return;
    tourStarted = true;
    if (returnToIntroTimer !== null) {
      clearTimeout(returnToIntroTimer);
      returnToIntroTimer = null;
    }
    gate.hidden = true;
    sessionStorage.setItem(GATE_PASSED_KEY, '1');

    initMap('map', (following) => {
      // Show re-centre button when user has manually panned away
      if (recenterBtn) recenterBtn.hidden = following;
    });

    _refreshMarkers(tour);

    // No route line: the four stops are numbered and metres apart in the town
    // centre, and the nav bar gives the live bearing + distance to the next one.

    // Render immediately from the fix that got us here, rather than waiting
    // for the next GPS update (which could be a few seconds away).
    if (lastFix) renderLiveUpdate(lastFix.lat, lastFix.lon);

    // Once per device, quietly pull the whole (fixed, small) tour area's tiles
    // into the service-worker cache so the map keeps working if signal drops
    // mid-walk and repeat visits make ~no tile requests.
    prewarmTourTiles();
  }

  if (startBtn) startBtn.addEventListener('click', () => startTour(lastFix));

  // Already passed the gate earlier this tour (e.g. returning from a
  // waypoint's detail page) — skip straight to live mode.
  if (sessionStorage.getItem(GATE_PASSED_KEY) === '1') {
    startTour(null);
  }

  const locationSvc = new LocationService();

  let locationStartTime = Date.now();
  let lastFix = null;

  function renderLiveUpdate(lat, lon) {
    updateUserLocation(lat, lon);
    panTo(lat, lon);

    // Boundary check
    boundaryWarn.hidden = !isOutsideBoundary(lat, lon);

    // Proximity — only fires once per waypoint
    const arrived = tour.checkProximity(lat, lon);
    if (arrived) {
      arrivalLink.textContent = `You've arrived at ${arrived.name} — tap to explore`;
      arrivalLink.href = `detail.html?id=${arrived.id}`;
      arrivalBanner.hidden = false;
      setTimeout(() => { arrivalBanner.hidden = true; }, 15000);
    }

    // Navigation instructions
    const wp = tour.getCurrentWaypoint();
    if (wp) {
      const dist    = haversineDistance(lat, lon, wp.latitude, wp.longitude);
      const bearing = bearingDeg(lat, lon, wp.latitude, wp.longitude);
      navText.textContent  = `${directionFromBearing(bearing)} to ${wp.name}`;
      distText.textContent = `${formatDistance(dist)} · ~${formatEta(dist)}`;
    }
  }

  locationSvc.start(
    ({ lat, lon, accuracy }) => {
      const elapsed = Date.now() - locationStartTime;
      const accuracyOk = accuracy <= ACCURACY_THRESHOLD_M;
      const timedOut   = elapsed >= 15_000;

      // Update the GPS badge regardless of accuracy
      if (gpsBadge) {
        if (!accuracyOk && !timedOut) {
          gpsBadge.textContent = `GPS: ±${Math.round(accuracy)}m (low)`;
        } else if (accuracy <= 10) {
          gpsBadge.textContent = `GPS: ±${Math.round(accuracy)}m ●`;
        } else if (accuracy <= 30) {
          gpsBadge.textContent = `GPS: ±${Math.round(accuracy)}m ◑`;
        } else {
          gpsBadge.textContent = `GPS: ±${Math.round(accuracy)}m ○`;
        }
      }

      // Skip inaccurate fixes until timeout
      if (!accuracyOk && !timedOut) return;

      lastFix = { lat, lon };

      if (!tourStarted) {
        // Gate mode: update the gate overlay only — no map, no route fetches.
        const outside = isOutsideBoundary(lat, lon);
        if (outside) {
          gateStatus.textContent =
            'You appear to be outside Bruff town area. Please start the ' +
            'application after you are in the main street for the best tour experience!';
          gateDistance.textContent =
            `${formatDistance(haversineDistance(lat, lon, BOUNDARY.CENTER_LAT, BOUNDARY.CENTER_LON))} away from Bruff town centre`;
          if (startBtn) startBtn.hidden = true;

          // Don't leave the user stuck on the gate — bounce back to the intro
          // screen after a few seconds if they're still outside the area.
          if (returnToIntroTimer === null) {
            returnToIntroTimer = setTimeout(() => {
              window.location.href = 'index.html';
            }, GATE_OUTSIDE_RETURN_DELAY_MS);
          }
        } else {
          if (returnToIntroTimer !== null) {
            clearTimeout(returnToIntroTimer);
            returnToIntroTimer = null;
          }
          gateStatus.textContent = "You're in Bruff — ready to start!";
          gateDistance.textContent = '';
          if (startBtn) startBtn.hidden = false;
        }
        return;
      }

      renderLiveUpdate(lat, lon);
    },
    (errMsg) => {
      // Without location we can't confirm the user is in the boundary —
      // bypass the gate directly rather than leaving them stuck on it.
      startTour(null);
      navText.textContent  = errMsg;
      distText.textContent = '';
      if (gpsBadge) gpsBadge.textContent = 'GPS: unavailable';
    },
  );

  // Re-render markers when returning to this page from detail.html
  window.addEventListener('pageshow', () => {
    if (!tourStarted) return;
    _refreshMarkers(tour);
    if (tour.isComplete()) {
      window.location.replace('complete.html');
    }
  });

  // Clean up location watcher and any pending gate timer when navigating away
  window.addEventListener('pagehide', () => {
    locationSvc.stop();
    if (returnToIntroTimer !== null) clearTimeout(returnToIntroTimer);
  });
}

function _refreshMarkers(tour) {
  addWaypointMarkers(WAYPOINTS, tour.getCurrentIndex(), tour.getVisitedIds());
}

// Marks that this device has already pre-fetched the tour-area tiles.
const TILE_PREWARM_KEY = 'bruff_tiles_prewarmed';

// Zoom range pre-fetched for offline use — 18 is already very detailed for a
// walking pace (the map's default zoom is 17.5).
const PREWARM_ZOOM_MIN = 15;
const PREWARM_ZOOM_MAX = 18;

/** slippy-map tile x/y for a lon/lat at a given zoom. */
function _lonLatToTile(lon, lat, z) {
  const n = 2 ** z;
  const x = Math.floor(((lon + 180) / 360) * n);
  const latRad = (lat * Math.PI) / 180;
  const y = Math.floor(
    ((1 - Math.asinh(Math.tan(latRad)) / Math.PI) / 2) * n,
  );
  return [x, y];
}

/**
 * One-time background fetch of every tile covering the tour boundary box, so
 * the service worker caches them and the map works offline mid-walk. Throttled
 * to a small pool; the SW's fetch handler does the actual caching.
 */
function prewarmTourTiles() {
  // Only worthwhile when a service worker is active to cache the results, and
  // only when online. (Also keeps this out of unit/e2e runs with no SW.)
  if (typeof navigator === 'undefined') return;
  if (!navigator.serviceWorker || !navigator.serviceWorker.controller) return;
  if (navigator.onLine === false) return;
  if (typeof fetch !== 'function') return;
  try {
    if (localStorage.getItem(TILE_PREWARM_KEY) === '1') return;
  } catch {
    return; // storage blocked — skip rather than re-fetch every visit
  }

  const halfW =
    BOUNDARY.WIDTH_KM /
    2 /
    (111 * Math.cos((BOUNDARY.CENTER_LAT * Math.PI) / 180));
  const halfH = BOUNDARY.HEIGHT_KM / 2 / 111;
  const west = BOUNDARY.CENTER_LON - halfW;
  const east = BOUNDARY.CENTER_LON + halfW;
  const north = BOUNDARY.CENTER_LAT + halfH;
  const south = BOUNDARY.CENTER_LAT - halfH;

  const urls = [];
  for (let z = PREWARM_ZOOM_MIN; z <= PREWARM_ZOOM_MAX; z++) {
    const [xMin, yMin] = _lonLatToTile(west, north, z);
    const [xMax, yMax] = _lonLatToTile(east, south, z);
    for (let x = xMin; x <= xMax; x++) {
      for (let y = yMin; y <= yMax; y++) {
        urls.push(`https://a.tile.openstreetmap.fr/hot/${z}/${x}/${y}.png`);
      }
    }
  }

  let i = 0;
  let done = 0;
  const pump = () => {
    if (i >= urls.length) return;
    const url = urls[i++];
    fetch(url)
      .catch(() => {})
      .finally(() => {
        done += 1;
        if (done === urls.length) {
          try {
            localStorage.setItem(TILE_PREWARM_KEY, '1');
          } catch {
            /* ignore */
          }
        }
        pump();
      });
  };
  const POOL = 4;
  for (let k = 0; k < Math.min(POOL, urls.length); k++) pump();
}

// ---------------------------------------------------------------------------
// Detail page  (detail.html?id=xxx)
// ---------------------------------------------------------------------------

export function initDetailPage() {
  const params = new URLSearchParams(window.location.search);
  const id = params.get('id');
  const waypoint = getWaypointById(id);
  const tour = new TourController(WAYPOINTS);

  const nameEl = document.getElementById('waypoint-name');
  const descEl = document.getElementById('waypoint-description');
  const histEl = document.getElementById('waypoint-history');
  const imgEl  = document.getElementById('waypoint-image');
  const btn    = document.getElementById('continue-btn');

  if (!waypoint) {
    nameEl.textContent = 'Waypoint not found';
    btn.disabled = true;
    return;
  }

  nameEl.textContent = waypoint.name;
  descEl.textContent = waypoint.description;
  histEl.textContent = waypoint.historicalInfo;
  document.title = `${waypoint.name} — Bruff Heritage Trail`;

  // Bundled photo first (offline, reliable) → remote URL → placeholder
  imgEl.alt = waypoint.name;
  const imageChain = [
    waypoint.localImage,
    waypoint.imageUrl,
    'assets/images/placeholder.jpg',
  ].filter(Boolean);
  let imageChainIdx = 0;
  imgEl.src = imageChain[0];
  imgEl.onerror = () => {
    imageChainIdx += 1;
    if (imageChainIdx < imageChain.length) imgEl.src = imageChain[imageChainIdx];
  };

  const isLast = tour.getCurrentIndex() === WAYPOINTS.length - 1;
  btn.textContent = isLast
    ? 'Complete Your Bruff Adventure'
    : 'Continue Your Bruff Journey →';

  btn.addEventListener('click', () => {
    const complete = tour.markCurrentVisited();
    window.location.href = complete ? 'complete.html' : 'tour.html';
  });
}

// ---------------------------------------------------------------------------
// Completion page  (complete.html)
// ---------------------------------------------------------------------------

export function initCompletePage() {
  const tour = new TourController(WAYPOINTS);
  const list       = document.getElementById('visited-list');
  const shareBtn   = document.getElementById('share-btn');
  const restartBtn = document.getElementById('restart-btn');

  // Render the visited waypoints summary
  WAYPOINTS.forEach((wp, i) => {
    const li = document.createElement('li');
    li.className = 'completed-item';
    li.innerHTML = `<span class="check" aria-hidden="true">&#10003;</span>
      <div><strong>${i + 1}. ${wp.name}</strong></div>`;
    list.appendChild(li);
  });

  restartBtn.addEventListener('click', () => {
    tour.reset();
    sessionStorage.removeItem(GATE_PASSED_KEY);
    window.location.href = 'index.html';
  });

  // Web Share API — show only if supported
  if (navigator.share) {
    shareBtn.hidden = false;
    shareBtn.addEventListener('click', () => {
      navigator
        .share({
          title: 'Bruff Heritage Trail',
          text: "I've just completed the Bruff Heritage Trail — 4 historic sites in Bruff, Co. Limerick!",
          url: window.location.origin + '/index.html',
        })
        .catch(() => {}); // User cancelled — ignore
    });
  }
}
