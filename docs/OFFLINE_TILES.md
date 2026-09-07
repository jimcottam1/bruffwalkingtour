# Map tiles & offline behaviour

## Why this exists

The tour covers **one fixed ~1.5 × 3 km area**. There is no reason for a device
to fetch its basemap tiles more than once, and a heritage-trail visitor may well
be walking with a foreign SIM or no data. The app is set up so tile traffic to
the third-party provider stays tiny.

## Provider

Basemap: **OpenStreetMap France, Humanitarian (HOT) style** —
`https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png`, keyless.

History: `tile.openstreetmap.org` blocked the app's traffic on real devices;
CARTO's keyless basemap then started serving an "API key required" watermark
tile once an IP crossed its anonymous quota. OSM France is a separate community
deployment with an informal fair-use policy (no bulk scraping, send a real
User-Agent — the app does).

## What the app already does (no setup needed)

1. **Persistent cache.** osmdroid's disk cache is configured to ~200 MB with a
   ~5-year tile expiry (`MainActivity.onCreate`), so once a tile for the tour
   area is fetched it is never re-requested or trimmed. The web PWA's service
   worker keeps up to 1500 tiles (`sw.js` `MAX_TILE_ENTRIES`).

2. **One-time pre-warm.** The first time a walk starts, the whole boundary box
   is pulled into the cache in the background:
   - Android — `MainActivity.prewarmTourTilesOnce()` via osmdroid's own
     `CacheManager` (polite throttling), zoom 15–18, guarded by the
     `tiles_prewarmed_v1` pref.
   - Web — `app.js` `prewarmTourTiles()`, zoom 15–18, a pool of 4 fetches,
     guarded by `localStorage['bruff_tiles_prewarmed']`, and only when a service
     worker is active to cache the results.

   That is ~800 tiles, once per install. After it completes the install makes
   essentially **zero** basemap requests for the tour, and the map keeps
   working if signal drops mid-walk.

### Will the provider's limit be hit?

For a single-town trail with a realistic audience: no. Each install is one
throttled ~800-tile burst, then nothing. Unlike CARTO there is no automatic
"over quota → watermark tile"; a problem would show up as HTTP 4xx/429 and be
recoverable. If the app ever gets thousands of daily users, move to a keyed
free tier (MapTiler 100k/mo, Stadia 200k/mo, …) or the static bundle below.

## Optional: ship a static tile bundle

Removes even the first-run fetch — the map works offline from the very first
launch. Only worth it if visitors routinely arrive with no data.

### 1. Generate the pack

You need a tile source **whose terms permit bulk/offline downloading** —
OSM's own servers do **not**. Use MapTiler / Stadia with a key, or your own
renderer.

```bash
node scripts/generate-offline-tiles.mjs --yes \
  --url "https://api.maptiler.com/maps/streets/{z}/{x}/{y}.png?key={key}" \
  --key YOUR_KEY --min-zoom 15 --max-zoom 18
```

Writes:
- `app/src/main/assets/OSMFranceHOT/{z}/{x}/{y}.png`
- `web/tiles/{z}/{x}/{y}.png`

Commit these — they are part of the shipped app.

### 2. Android — nothing else to do

osmdroid's default provider chain includes an **assets** provider that reads
`assets/<tile-source-name>/{z}/{x}/{y}.png` before going to the network. The
tile source is named `OSMFranceHOT` (`MainActivity.OSM_HOT`), so the folder
above is picked up automatically; anything missing still falls back online.

If you generate from a different provider, either keep the folder name
`OSMFranceHOT` or rename both the folder and the `XYTileSource` name so they
match.

### 3. Web — one change in `web/js/map.js`

Point the tile layer at the local pack first, with the remote URL as fallback:

```js
const localTiles = L.tileLayer('./tiles/{z}/{x}/{y}.png', {
  maxZoom: 20,
  attribution:
    '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
});
localTiles.on('tileerror', (e) => {
  // missing from the pack — fall back to the network tile
  e.tile.src =
    `https://a.tile.openstreetmap.fr/hot/${e.coords.z}/${e.coords.x}/${e.coords.y}.png`;
});
localTiles.addTo(_map);
```

Then add `web/tiles/` to the service worker precache (`APP_SHELL` in `sw.js`)
or let it cache on first use.

## Tile-count reference (Bruff boundary)

| Max zoom | tiles (z15–max) | ≈ size |
|----------|-----------------|--------|
| 17       | ~210            | ~2 MB  |
| 18       | ~800            | ~9 MB  |
| 19       | ~3,000          | ~35 MB |

(Run the generator without `--yes` to print the exact count for your zoom range.)

z18 is already very detailed for walking pace (the map's default zoom is 17.5).
