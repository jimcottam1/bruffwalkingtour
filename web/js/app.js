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
import { getRoute } from './routing.js';
import {
  initMap,
  addWaypointMarkers,
  updateUserLocation,
  drawRoute,
  panTo,
} from './map.js';

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

  const navText = document.getElementById('navigation-instruction');
  const distText = document.getElementById('distance-info');
  const boundaryWarning = document.getElementById('boundary-warning');
  const arrivalBanner = document.getElementById('arrival-banner');
  const arrivalLink = document.getElementById('arrival-link');

  const map = initMap('map');

  // Draw markers for current state
  _refreshMarkers(tour);

  // Draw initial route from tour centre → first waypoint as placeholder
  const firstWp = tour.getCurrentWaypoint();
  if (firstWp) {
    getRoute(
      BOUNDARY.CENTER_LAT,
      BOUNDARY.CENTER_LON,
      firstWp.latitude,
      firstWp.longitude,
    ).then((pts) => drawRoute(pts));
  }

  if (!LocationService.isSupported()) {
    navText.textContent = 'Location services are not available on this device.';
    return;
  }

  const locationSvc = new LocationService();

  locationSvc.start(
    ({ lat, lon }) => {
      updateUserLocation(lat, lon);
      panTo(lat, lon);

      // Boundary check
      if (isOutsideBoundary(lat, lon)) {
        boundaryWarning.hidden = false;
      } else {
        boundaryWarning.hidden = true;
      }

      // Proximity — only fires once per waypoint
      const arrived = tour.checkProximity(lat, lon);
      if (arrived) {
        arrivalLink.textContent = `You've arrived at ${arrived.name} — tap to explore`;
        arrivalLink.href = `detail.html?id=${arrived.id}`;
        arrivalBanner.hidden = false;
        setTimeout(() => {
          arrivalBanner.hidden = true;
        }, 15000);
      }

      // Navigation instructions
      const wp = tour.getCurrentWaypoint();
      if (wp) {
        const dist = haversineDistance(lat, lon, wp.latitude, wp.longitude);
        const bearing = bearingDeg(lat, lon, wp.latitude, wp.longitude);
        navText.textContent = `${directionFromBearing(bearing)} to ${wp.name}`;
        distText.textContent = `${formatDistance(dist)} · ~${formatEta(dist)}`;

        // Refresh route from current location
        getRoute(lat, lon, wp.latitude, wp.longitude).then((pts) => drawRoute(pts));
      }
    },
    (errMsg) => {
      navText.textContent = errMsg;
      distText.textContent = '';
    },
  );

  // Re-render markers when returning to this page from detail.html
  window.addEventListener('pageshow', () => {
    _refreshMarkers(tour);
    if (tour.isComplete()) {
      window.location.replace('complete.html');
    }
  });

  // Clean up location watcher when navigating away
  window.addEventListener('pagehide', () => locationSvc.stop());
}

function _refreshMarkers(tour) {
  addWaypointMarkers(WAYPOINTS, tour.getCurrentIndex(), tour.getVisitedIds());
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
  const imgEl = document.getElementById('waypoint-image');
  const btn = document.getElementById('continue-btn');

  if (!waypoint) {
    nameEl.textContent = 'Waypoint not found';
    btn.disabled = true;
    return;
  }

  nameEl.textContent = waypoint.name;
  descEl.textContent = waypoint.description;
  histEl.textContent = waypoint.historicalInfo;
  document.title = `${waypoint.name} — Bruff Heritage Trail`;

  // Image with fallback chain: remote URL → local file → placeholder
  imgEl.alt = waypoint.name;
  imgEl.src = waypoint.imageUrl || waypoint.localImage || 'assets/images/placeholder.jpg';
  imgEl.onerror = () => {
    if (waypoint.localImage && imgEl.src !== new URL(waypoint.localImage, location.href).href) {
      imgEl.src = waypoint.localImage;
    } else {
      imgEl.src = 'assets/images/placeholder.jpg';
    }
  };

  const isLast = tour.getCurrentIndex() === WAYPOINTS.length - 1;
  btn.textContent = isLast
    ? 'Complete Your Bruff Adventure'
    : 'Continue Your Bruff Journey \u2192';

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
  const list = document.getElementById('visited-list');
  const shareBtn = document.getElementById('share-btn');
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
