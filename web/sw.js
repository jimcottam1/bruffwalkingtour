/**
 * Service Worker for Bruff Heritage Trail PWA.
 *
 * Strategies:
 *  - App shell (HTML/CSS/JS/images): cache-first, network fallback
 *  - Map tiles (OSM France HOT): stale-while-revalidate, capped at MAX_TILE_ENTRIES
 *
 * Bump CACHE_VERSION to force all clients to download a fresh app shell.
 */

// v3: route line + routing.js removed from the app shell.
// v4: placeholder.jpg now exists (its absence made cache.addAll — and so the
//     whole service-worker install — fail); hard location gate; church icons.
const CACHE_VERSION = 'v4';
const APP_CACHE  = `bruff-app-${CACHE_VERSION}`;
const TILE_CACHE = `bruff-tiles-${CACHE_VERSION}`;
// Big enough to hold the whole fixed tour area (z15–18 over ~1.5×3 km, a few
// hundred tiles — pre-warmed by app.js when a walk starts) plus live panning.
const MAX_TILE_ENTRIES = 1500;

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

  // Map tile requests — stale-while-revalidate with size cap
  if (url.hostname.endsWith('tile.openstreetmap.fr') || url.hostname.endsWith('tile.openstreetmap.org')) {
    event.respondWith(handleTile(event.request));
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
