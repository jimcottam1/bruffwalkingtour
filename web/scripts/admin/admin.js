// Client-side logic for the local waypoints admin page. No build step —
// loaded directly as a <script type="module"> alongside the Leaflet CDN script.

let waypoints = [];
let boundary = null;
let selectedIndex = -1;
let map;
let markers = [];

const el = (id) => document.getElementById(id);
const listEl = el('waypoint-list');
const statusEl = el('status');
const form = el('waypoint-form');

const fields = {
  id: el('f-id'),
  name: el('f-name'),
  latitude: el('f-lat'),
  longitude: el('f-lon'),
  proximityRadius: el('f-radius'),
  description: el('f-description'),
  historicalInfo: el('f-historical'),
  imageUrl: el('f-image-url'),
  localImage: el('f-local-image'),
};

function setStatus(message, kind) {
  statusEl.textContent = message;
  statusEl.className = `status ${kind ?? ''}`;
}

function slugify(name) {
  const base = String(name)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
  return base || 'waypoint';
}

function uniqueId(base) {
  const existing = new Set(waypoints.map((w) => w.id));
  if (!existing.has(base)) return base;
  let i = 2;
  while (existing.has(`${base}_${i}`)) i++;
  return `${base}_${i}`;
}

// ---- Map -------------------------------------------------------------------

function initMap() {
  map = L.map('map').setView([boundary.centerLat, boundary.centerLon], 15);
  L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
    attribution: '&copy; OpenStreetMap &copy; CARTO',
    maxZoom: 20,
  }).addTo(map);

  const halfWidthDeg =
    boundary.widthKm / 2 / (111.0 * Math.cos((boundary.centerLat * Math.PI) / 180));
  const halfHeightDeg = boundary.heightKm / 2 / 111.0;
  L.rectangle(
    [
      [boundary.centerLat - halfHeightDeg, boundary.centerLon - halfWidthDeg],
      [boundary.centerLat + halfHeightDeg, boundary.centerLon + halfWidthDeg],
    ],
    { color: '#c8922a', weight: 1, dashArray: '4 4', fill: false, interactive: false }
  ).addTo(map);

  map.on('click', (e) => {
    if (selectedIndex < 0) return;
    waypoints[selectedIndex].latitude = e.latlng.lat;
    waypoints[selectedIndex].longitude = e.latlng.lng;
    fields.latitude.value = e.latlng.lat.toFixed(6);
    fields.longitude.value = e.latlng.lng.toFixed(6);
    renderMarkers();
  });
}

function renderMarkers() {
  markers.forEach((m) => m.remove());
  markers = waypoints.map((wp, index) => {
    const marker = L.marker([wp.latitude, wp.longitude], { draggable: true })
      .addTo(map)
      .bindTooltip(`${index + 1}. ${wp.name}`);

    marker.on('click', () => selectWaypoint(index));
    marker.on('dragend', () => {
      const { lat, lng } = marker.getLatLng();
      waypoints[index].latitude = lat;
      waypoints[index].longitude = lng;
      if (index === selectedIndex) {
        fields.latitude.value = lat.toFixed(6);
        fields.longitude.value = lng.toFixed(6);
      }
    });

    return marker;
  });
  highlightSelectedMarker();
}

function highlightSelectedMarker() {
  markers.forEach((m, i) => {
    const el2 = m.getElement();
    if (el2) el2.style.filter = i === selectedIndex ? 'hue-rotate(120deg)' : '';
  });
}

// ---- List --------------------------------------------------------------

function renderList() {
  listEl.innerHTML = '';
  waypoints.forEach((wp, index) => {
    const li = document.createElement('li');
    li.className = 'waypoint-item' + (index === selectedIndex ? ' selected' : '');

    const num = document.createElement('span');
    num.className = 'num';
    num.textContent = index + 1;

    const name = document.createElement('span');
    name.className = 'name';
    name.textContent = wp.name || '(unnamed)';

    const reorder = document.createElement('span');
    reorder.className = 'reorder';
    const up = document.createElement('button');
    up.type = 'button';
    up.textContent = '▲';
    up.title = 'Move earlier in tour order';
    up.disabled = index === 0;
    up.addEventListener('click', (e) => { e.stopPropagation(); moveWaypoint(index, -1); });
    const down = document.createElement('button');
    down.type = 'button';
    down.textContent = '▼';
    down.title = 'Move later in tour order';
    down.disabled = index === waypoints.length - 1;
    down.addEventListener('click', (e) => { e.stopPropagation(); moveWaypoint(index, 1); });
    reorder.append(up, down);

    li.append(num, name, reorder);
    li.addEventListener('click', () => selectWaypoint(index));
    listEl.appendChild(li);
  });
}

function moveWaypoint(index, delta) {
  const target = index + delta;
  if (target < 0 || target >= waypoints.length) return;
  [waypoints[index], waypoints[target]] = [waypoints[target], waypoints[index]];
  if (selectedIndex === index) selectedIndex = target;
  else if (selectedIndex === target) selectedIndex = index;
  renderList();
  renderMarkers();
}

// ---- Form ----------------------------------------------------------------

function selectWaypoint(index) {
  selectedIndex = index;
  const wp = waypoints[index];
  fields.id.value = wp.id;
  fields.name.value = wp.name;
  fields.latitude.value = wp.latitude;
  fields.longitude.value = wp.longitude;
  fields.proximityRadius.value = wp.proximityRadius;
  fields.description.value = wp.description;
  fields.historicalInfo.value = wp.historicalInfo;
  fields.imageUrl.value = wp.imageUrl ?? '';
  fields.localImage.value = wp.localImage ?? '';
  renderList();
  highlightSelectedMarker();
}

function bindFieldSync(field, key, transform = (v) => v) {
  field.addEventListener('input', () => {
    if (selectedIndex < 0) return;
    waypoints[selectedIndex][key] = transform(field.value);
    if (key === 'name') renderList();
    if (key === 'latitude' || key === 'longitude') {
      const wp = waypoints[selectedIndex];
      if (Number.isFinite(wp.latitude) && Number.isFinite(wp.longitude) && markers[selectedIndex]) {
        markers[selectedIndex].setLatLng([wp.latitude, wp.longitude]);
      }
    }
  });
}

bindFieldSync(fields.id, 'id');
bindFieldSync(fields.name, 'name');
bindFieldSync(fields.latitude, 'latitude', Number);
bindFieldSync(fields.longitude, 'longitude', Number);
bindFieldSync(fields.proximityRadius, 'proximityRadius', Number);
bindFieldSync(fields.description, 'description');
bindFieldSync(fields.historicalInfo, 'historicalInfo');
bindFieldSync(fields.imageUrl, 'imageUrl');
bindFieldSync(fields.localImage, 'localImage', (v) => v || null);

// ---- Add / delete --------------------------------------------------------

el('add-btn').addEventListener('click', () => {
  const id = uniqueId('new_waypoint');
  waypoints.push({
    id,
    name: 'New Waypoint',
    description: '',
    historicalInfo: '',
    latitude: boundary.centerLat,
    longitude: boundary.centerLon,
    proximityRadius: 20,
    imageUrl: '',
    localImage: null,
  });
  renderList();
  renderMarkers();
  selectWaypoint(waypoints.length - 1);
});

el('delete-btn').addEventListener('click', () => {
  if (selectedIndex < 0) return;
  const wp = waypoints[selectedIndex];
  if (!confirm(`Delete "${wp.name}"? This cannot be undone once saved.`)) return;
  waypoints.splice(selectedIndex, 1);
  selectedIndex = Math.min(selectedIndex, waypoints.length - 1);
  renderList();
  renderMarkers();
  if (selectedIndex >= 0) selectWaypoint(selectedIndex);
  else form.reset();
});

// ---- Save ------------------------------------------------------------------

el('save-btn').addEventListener('click', async () => {
  setStatus('Saving…');
  try {
    const res = await fetch('/api/waypoints', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ waypoints }),
    });
    const body = await res.json();
    if (!res.ok || !body.ok) {
      setStatus(body.error || 'Save failed', 'err');
      return;
    }
    setStatus('Saved and regenerated ✓', 'ok');
  } catch (err) {
    setStatus(`Save failed: ${err.message}`, 'err');
  }
});

// ---- Load --------------------------------------------------------------

async function load() {
  const res = await fetch('/api/waypoints');
  const body = await res.json();
  waypoints = body.waypoints;
  boundary = body.boundary;
  initMap();
  renderList();
  renderMarkers();
  if (waypoints.length > 0) selectWaypoint(0);
}

load();
