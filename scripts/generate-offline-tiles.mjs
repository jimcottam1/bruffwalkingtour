#!/usr/bin/env node
/**
 * Generate a static offline tile pack for the fixed Bruff tour area.
 *
 * This is OPTIONAL. The app already pre-warms tiles into the on-device cache
 * the first time a walk starts (see MainActivity.prewarmTourTilesOnce and
 * app.js prewarmTourTiles), which is enough for normal use. Run this only if
 * you want the map to work on the very first launch with no data at all.
 *
 *   node scripts/generate-offline-tiles.mjs --yes
 *
 * Output:
 *   app/src/main/assets/OSMFranceHOT/{z}/{x}/{y}.png   ← osmdroid picks this up
 *                                                        automatically (asset
 *                                                        tile provider), no
 *                                                        code change needed.
 *   web/tiles/{z}/{x}/{y}.png                          ← see docs/OFFLINE_TILES.md
 *                                                        for the one-line map.js
 *                                                        change to use it.
 *
 * Flags:
 *   --yes                 required — confirms you have the right to bulk-download
 *                         from --url (OSM's own tile servers do NOT permit this;
 *                         use a provider whose terms allow offline packs, e.g.
 *                         MapTiler / Stadia with a key, or your own renderer).
 *   --url <template>      tile URL template, default the app's OSM France HOT.
 *                         Tokens: {z} {x} {y}  (and {key} -> --key)
 *   --key <apikey>        substituted for {key} in --url
 *   --min-zoom <n>        default 15
 *   --max-zoom <n>        default 18   (19 ≈ +2000 tiles, rarely worth it)
 *   --concurrency <n>     default 4
 *   --user-agent <str>    default the app's UA string
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

function arg(name, def) {
  const i = process.argv.indexOf(`--${name}`);
  if (i === -1) return def;
  const v = process.argv[i + 1];
  return v && !v.startsWith('--') ? v : true;
}

const CONFIRMED = arg('yes', false) === true;
const URL_TEMPLATE = String(
  arg('url', 'https://a.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png'),
);
const API_KEY = arg('key', '');
const MIN_ZOOM = Number(arg('min-zoom', 15));
const MAX_ZOOM = Number(arg('max-zoom', 18));
const CONCURRENCY = Number(arg('concurrency', 4));
const USER_AGENT = String(
  arg(
    'user-agent',
    'BruffWalkingTour/offline-pack (+https://github.com/jimcottam1/bruffwalkingtour)',
  ),
);

const ANDROID_OUT = path.join(ROOT, 'app/src/main/assets/OSMFranceHOT');
const WEB_OUT = path.join(ROOT, 'web/tiles');

// ---- boundary (parsed from the single source of truth) --------------------

function readBoundary() {
  const yaml = fs.readFileSync(path.join(ROOT, 'data/waypoints.yaml'), 'utf8');
  const block = yaml.slice(yaml.indexOf('boundary:'));
  const num = (k) => {
    const m = block.match(new RegExp(`${k}:\\s*(-?[0-9.]+)`));
    if (!m) throw new Error(`boundary.${k} not found in data/waypoints.yaml`);
    return Number(m[1]);
  };
  return {
    centerLat: num('centerLat'),
    centerLon: num('centerLon'),
    widthKm: num('widthKm'),
    heightKm: num('heightKm'),
  };
}

function lonLatToTile(lon, lat, z) {
  const n = 2 ** z;
  const x = Math.floor(((lon + 180) / 360) * n);
  const latRad = (lat * Math.PI) / 180;
  const y = Math.floor(
    ((1 - Math.asinh(Math.tan(latRad)) / Math.PI) / 2) * n,
  );
  return [x, y];
}

// ---- main ----------------------------------------------------------------

async function main() {
  const b = readBoundary();
  const halfW =
    b.widthKm / 2 / (111 * Math.cos((b.centerLat * Math.PI) / 180));
  const halfH = b.heightKm / 2 / 111;
  const west = b.centerLon - halfW;
  const east = b.centerLon + halfW;
  const north = b.centerLat + halfH;
  const south = b.centerLat - halfH;

  const jobs = [];
  for (let z = MIN_ZOOM; z <= MAX_ZOOM; z++) {
    const [xMin, yMin] = lonLatToTile(west, north, z);
    const [xMax, yMax] = lonLatToTile(east, south, z);
    for (let x = xMin; x <= xMax; x++) {
      for (let y = yMin; y <= yMax; y++) jobs.push({ z, x, y });
    }
  }

  console.log(
    `Boundary: ${b.widthKm}×${b.heightKm} km around ${b.centerLat}, ${b.centerLon}`,
  );
  console.log(`Zoom ${MIN_ZOOM}–${MAX_ZOOM} → ${jobs.length} tiles`);
  console.log(`Source: ${URL_TEMPLATE}`);

  if (!CONFIRMED) {
    console.error(
      '\nRefusing to run without --yes.\n' +
        "OSM's own tile servers (openstreetmap.org / openstreetmap.fr) do NOT\n" +
        'permit bulk downloading. Point --url at a provider whose terms allow\n' +
        'offline packs, then re-run with --yes.\n',
    );
    process.exit(1);
  }

  let done = 0;
  let failed = 0;
  const queue = jobs.slice();

  async function worker() {
    while (queue.length) {
      const { z, x, y } = queue.shift();
      const url = URL_TEMPLATE.replace('{z}', z)
        .replace('{x}', x)
        .replace('{y}', y)
        .replace('{key}', API_KEY);
      try {
        const res = await fetch(url, { headers: { 'User-Agent': USER_AGENT } });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const buf = Buffer.from(await res.arrayBuffer());
        for (const base of [ANDROID_OUT, WEB_OUT]) {
          const dir = path.join(base, String(z), String(x));
          fs.mkdirSync(dir, { recursive: true });
          fs.writeFileSync(path.join(dir, `${y}.png`), buf);
        }
      } catch (e) {
        failed++;
        console.warn(`  ${z}/${x}/${y}  failed: ${e.message}`);
      }
      if (++done % 50 === 0 || done === jobs.length) {
        console.log(`  ${done}/${jobs.length}`);
      }
    }
  }

  await Promise.all(
    Array.from({ length: Math.max(1, CONCURRENCY) }, worker),
  );

  console.log(
    `\nDone. ${done - failed} tiles written, ${failed} failed.\n` +
      `  Android: ${path.relative(ROOT, ANDROID_OUT)}/  (used automatically)\n` +
      `  Web:     ${path.relative(ROOT, WEB_OUT)}/  (see docs/OFFLINE_TILES.md)\n`,
  );
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
