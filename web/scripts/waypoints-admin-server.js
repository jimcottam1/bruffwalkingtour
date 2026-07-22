#!/usr/bin/env node
// Local-only GUI for editing data/waypoints.yaml.
// Usage (from web/): npm run admin:waypoints

import { createServer } from 'http';
import { readFileSync, existsSync } from 'fs';
import { execFileSync } from 'child_process';
import { fileURLToPath } from 'url';
import { dirname, join, extname, normalize } from 'path';
import { loadWaypointsData, saveWaypointsData, WEB_DIR } from './waypoints-io.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ADMIN_DIR = join(__dirname, 'admin');
const GENERATE_SCRIPT = join(__dirname, 'generate-waypoints.js');
const PORT = 5175;
const HOST = '127.0.0.1';

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
};

function serveStatic(req, res) {
  const path = req.url === '/' ? '/index.html' : req.url;
  const filePath = normalize(join(ADMIN_DIR, path));
  if (!filePath.startsWith(ADMIN_DIR) || !existsSync(filePath)) {
    res.writeHead(404).end('Not found');
    return;
  }
  const contentType = MIME[extname(filePath)] ?? 'application/octet-stream';
  res.writeHead(200, { 'Content-Type': contentType }).end(readFileSync(filePath));
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', (chunk) => { body += chunk; });
    req.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch (err) {
        reject(new Error('Invalid JSON body'));
      }
    });
    req.on('error', reject);
  });
}

function validateWaypoints(waypoints) {
  if (!Array.isArray(waypoints) || waypoints.length === 0) {
    return 'At least one waypoint is required';
  }
  const ids = new Set();
  for (const wp of waypoints) {
    if (typeof wp.id !== 'string' || !/^[a-z0-9_]+$/.test(wp.id)) {
      return `Invalid id "${wp.id}" — use lowercase letters, numbers, underscores only`;
    }
    if (ids.has(wp.id)) return `Duplicate id "${wp.id}"`;
    ids.add(wp.id);
    if (typeof wp.name !== 'string' || !wp.name.trim()) {
      return `Waypoint "${wp.id}" is missing a name`;
    }
    if (!Number.isFinite(wp.latitude) || !Number.isFinite(wp.longitude)) {
      return `Waypoint "${wp.id}" has invalid coordinates`;
    }
    if (!Number.isFinite(wp.proximityRadius) || wp.proximityRadius <= 0) {
      return `Waypoint "${wp.id}" needs a positive proximity radius`;
    }
    if (typeof wp.description !== 'string' || typeof wp.historicalInfo !== 'string') {
      return `Waypoint "${wp.id}" is missing description/historicalInfo`;
    }
  }
  return null;
}

async function handleGetWaypoints(req, res) {
  const data = loadWaypointsData();
  const waypoints = data.waypoints.map((wp) => ({
    ...wp,
    description: wp.description.replace(/\n+$/, ''),
    historicalInfo: wp.historicalInfo.replace(/\n+$/, ''),
  }));
  res.writeHead(200, { 'Content-Type': 'application/json' })
    .end(JSON.stringify({ waypoints, boundary: data.boundary }));
}

async function handleSaveWaypoints(req, res) {
  let payload;
  try {
    payload = await readJsonBody(req);
  } catch (err) {
    res.writeHead(400, { 'Content-Type': 'application/json' })
      .end(JSON.stringify({ ok: false, error: err.message }));
    return;
  }

  const error = validateWaypoints(payload.waypoints);
  if (error) {
    res.writeHead(400, { 'Content-Type': 'application/json' })
      .end(JSON.stringify({ ok: false, error }));
    return;
  }

  const existing = loadWaypointsData();
  saveWaypointsData({ tour: existing.tour, boundary: existing.boundary, waypoints: payload.waypoints });

  try {
    execFileSync(process.execPath, [GENERATE_SCRIPT], { cwd: WEB_DIR, stdio: 'pipe' });
  } catch (err) {
    res.writeHead(500, { 'Content-Type': 'application/json' })
      .end(JSON.stringify({ ok: false, error: `Saved, but generator failed: ${err.message}` }));
    return;
  }

  res.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify({ ok: true }));
}

const server = createServer(async (req, res) => {
  try {
    if (req.url === '/api/waypoints' && req.method === 'GET') {
      await handleGetWaypoints(req, res);
    } else if (req.url === '/api/waypoints' && req.method === 'POST') {
      await handleSaveWaypoints(req, res);
    } else if (req.method === 'GET') {
      serveStatic(req, res);
    } else {
      res.writeHead(405).end('Method not allowed');
    }
  } catch (err) {
    res.writeHead(500, { 'Content-Type': 'application/json' })
      .end(JSON.stringify({ ok: false, error: err.message }));
  }
});

server.listen(PORT, HOST, () => {
  console.log(`Waypoints admin running at http://${HOST}:${PORT} (local only, Ctrl+C to stop)`);
});
