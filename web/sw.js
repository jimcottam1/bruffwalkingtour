/**
 * Service Worker for Bruff Heritage Trail PWA.
 *
 * Strategies:
 *  - App shell (HTML/CSS/JS/images): cache-first, network fallback
 *  - OSM map tiles: stale-while-revalidate, capped at MAX_TILE_ENTRIES
 *  - OSRM routing API: network-only (fallback handled in routing.js)
 *
 * Bump CACHE_VERSION to force all clients to download a fresh app shell.
 */

const CACHE_VERSION = 'v1';
const APP_CACHE  = `bruff-app-${CACHE_VERSION}`;
const TILE_CACHE = `bruff-tiles-${CACHE_VERSION}`;
const MAX_TILE_ENTRIES = 200;

const APP_SHELL = [
  './',
  './index.html',
  './tour.html',
  './detail.html',
  './complete.html',
  './manifest.json',
  './css/main.css',
  './css/tour.css',
  './css/pages.css',
  './css/detail.css',
  './js/data.js',
  './js/utils.js',
  './js/tour.js',
  './js/location.js',
  './js/routing.js',
  './js/map.js',
  './js/app.js',
  './assets/images/thomas_fitzgerald_centre.jpg',
  './assets/images/placeholder.jpg',
  // Leaflet from CDN
  'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js',
  'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css',
];

// ---- Install ---------------------------------------------------------------
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(APP_CACHE)
      .then((cache) => cache.addAll(APP_SHELL))
      .then(() => self.skipWaiting()),
  );
});

// ---- Activate --------------------------------------------------------------
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((k) => k !== APP_CACHE && k !== TILE_CACHE)
            .map((k) => caches.delete(k)),
        ),
      )
      .then(() => self.clients.claim()),
  );
});

// ---- Fetch -----------------------------------------------------------------
self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // OSM tile requests — stale-while-revalidate with size cap
  if (url.hostname.endsWith('tile.openstreetmap.org')) {
    event.respondWith(handleTile(event.request));
    return;
  }

  // OSRM API — always network; routing.js handles offline fallback itself
  if (url.hostname === 'router.project-osrm.org') {
    event.respondWith(
      fetch(event.request).catch(() => new Response('', { status: 503 })),
    );
    return;
  }

  // Everything else — cache-first, fetch + cache on miss
  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) return cached;
      return fetch(event.request)
        .then((response) => {
          if (response.ok) {
            caches
              .open(APP_CACHE)
              .then((cache) => cache.put(event.request, response.clone()));
          }
          return response;
        })
        .catch(() => caches.match('./index.html'));
    }),
  );
});

// ---- Tile handler ----------------------------------------------------------
async function handleTile(request) {
  const cache = await caches.open(TILE_CACHE);
  const cached = await cache.match(request);

  // Kick off network fetch in background to keep tiles fresh
  const networkFetch = fetch(request)
    .then(async (response) => {
      if (response.ok) {
        await cache.put(request, response.clone());
        await evictOldTiles(cache);
      }
      return response;
    })
    .catch(() => null);

  // Return cached immediately if available, otherwise await network
  return cached || (await networkFetch) || new Response('', { status: 503 });
}

async function evictOldTiles(cache) {
  const keys = await cache.keys();
  if (keys.length > MAX_TILE_ENTRIES) {
    // FIFO: delete oldest entries (keys are insertion-ordered)
    const excess = keys.slice(0, keys.length - MAX_TILE_ENTRIES);
    await Promise.all(excess.map((k) => cache.delete(k)));
  }
}
