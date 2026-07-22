// Shared read/write helpers for data/waypoints.yaml.
// Used by generate-waypoints.js (read-only) and the waypoints admin server (read+write).

import { readFileSync, writeFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import yaml from 'js-yaml';

const __dirname = dirname(fileURLToPath(import.meta.url));
export const WEB_DIR = join(__dirname, '..');
export const ROOT = join(WEB_DIR, '..');
export const YAML_PATH = join(ROOT, 'data', 'waypoints.yaml');

const HEADER = `# Single source of truth for the Bruff Heritage Trail tour data.
#
# Edit this file to add, remove, or correct a waypoint. Then run, from web/:
#   npm run gen:waypoints
# to regenerate:
#   - app/src/main/java/com/example/bruffwalkingtour/BruffTourData.kt
#   - web/js/data.js
# Do NOT hand-edit those two generated files directly — changes will be
# overwritten the next time the generator runs.
#
# (Waypoints can also be edited with the local GUI: npm run admin:waypoints)
`;

export function loadWaypointsData() {
  const data = yaml.load(readFileSync(YAML_PATH, 'utf8'));

  if (!data?.tour || !data?.boundary || !Array.isArray(data?.waypoints)) {
    throw new Error('waypoints.yaml is missing tour/boundary/waypoints data');
  }

  return data;
}

function yamlScalar(value) {
  // Quote only when needed; js-yaml's own quoting rules are more permissive
  // than we want for a hand-maintained file, so keep this deliberately simple.
  const str = String(value);
  if (str === '' || /^[\s]|[\s]$/.test(str) || /[:#{}\[\],&*!|>'"%@`]/.test(str)) {
    return JSON.stringify(str);
  }
  return str;
}

function yamlBlockScalar(text, indent) {
  const pad = ' '.repeat(indent);
  const lines = String(text).replace(/\n+$/, '').split('\n');
  return lines.map((line) => `${pad}${line}`).join('\n');
}

export function serializeWaypointsYaml({ tour, boundary, waypoints }) {
  const lines = [HEADER];

  lines.push('tour:');
  lines.push(`  id: ${yamlScalar(tour.id)}`);
  lines.push(`  name: ${yamlScalar(tour.name)}`);
  lines.push(`  description: ${yamlScalar(tour.description)}`);
  lines.push(`  estimatedDurationMinutes: ${tour.estimatedDurationMinutes}`);
  lines.push(`  difficulty: ${yamlScalar(tour.difficulty)}`);
  lines.push('');

  lines.push('# Rectangular geo-fence centred on Sean Wall Monument.');
  lines.push('boundary:');
  lines.push(`  centerLat: ${boundary.centerLat}`);
  lines.push(`  centerLon: ${boundary.centerLon}`);
  lines.push(`  widthKm: ${boundary.widthKm}`);
  lines.push(`  heightKm: ${boundary.heightKm}`);
  lines.push('');

  lines.push('waypoints:');
  waypoints.forEach((wp, index) => {
    lines.push(`  - id: ${yamlScalar(wp.id)}`);
    lines.push(`    name: ${yamlScalar(wp.name)}`);
    lines.push('    description: |');
    lines.push(yamlBlockScalar(wp.description, 6));
    lines.push('    historicalInfo: |');
    lines.push(yamlBlockScalar(wp.historicalInfo, 6));
    lines.push(`    latitude: ${wp.latitude}`);
    lines.push(`    longitude: ${wp.longitude}`);
    lines.push(`    proximityRadius: ${wp.proximityRadius}`);
    lines.push(`    imageUrl: ${yamlScalar(wp.imageUrl)}`);
    lines.push(`    localImage: ${wp.localImage ? yamlScalar(wp.localImage) : 'null'}`);
    if (index < waypoints.length - 1) lines.push('');
  });
  lines.push('');

  return lines.join('\n');
}

export function saveWaypointsData(data) {
  writeFileSync(YAML_PATH, serializeWaypointsYaml(data), 'utf8');
}
