// Diorama Addons web map.
// Top-down style: map units are blocks, lng = x, lat = -z.
// Diorama (oblique) style: lng = u = x, lat = -v with v = z*sin - y*cos (the camera looks north and down).
(async function () {
  'use strict';
  const info = await (await fetch('api/info')).json();
  const proj = info.projection || { type: 'topdown' };
  const OBLIQUE = proj.type === 'oblique';
  const zoomOut = info.zoomOutLevels;
  const tileSize = info.tileSize;
  // Leaflet zoom at which level-0 tiles are shown pixel for pixel.
  const NATIVE = OBLIQUE ? Math.round(Math.log2(proj.pixelsPerUnit)) : 0;
  const UNITS0 = OBLIQUE ? proj.tileUnits : tileSize;

  document.getElementById('title').textContent = info.name || 'World Map';
  document.title = info.name || 'World Map';

  const map = L.map('map', {
    crs: L.CRS.Simple,
    minZoom: NATIVE - zoomOut,
    maxZoom: NATIVE + 3,
    zoomSnap: 1,
    attributionControl: false,
  });

  const vOf = (y, z) => (OBLIQUE ? z * proj.sin - y * proj.cos : z);
  const toLatLng = (x, y, z) => L.latLng(-vOf(y, z), x);

  const MapTiles = L.TileLayer.extend({
    getTileUrl(coords) {
      return `tiles/${this.options.dim}/${NATIVE - coords.z}/${coords.x}/${coords.y}.png`;
    },
  });

  // ------------------------------------------------------------------ state
  let current = null;
  let tiles = null;
  let claims = [];
  let claimByChunk = new Map(); // "cx,cz" -> claim index, current dimension
  let players = [];
  const hitCache = new Map(); // "level/x/y" -> Uint8ClampedArray (RGBA) or null

  const vectorClaims = L.layerGroup();
  const labelsLayer = L.layerGroup();
  const playersLayer = L.layerGroup();
  const overlays = {};

  // ------------------------------------------------------------------ hit maps (oblique)
  const zRef = (ty, level) => Math.floor((ty * UNITS0 * 2 ** level + current.minY * proj.cos) / proj.sin) - 1;
  const claimPositions = new Map(); // claim index -> {u, v, n} gathered from the tiles drawn

  const HitLayer = L.GridLayer.extend({
    createTile(coords, done) {
      const canvas = L.DomUtil.create('canvas');
      canvas.width = canvas.height = tileSize;
      const level = NATIVE - coords.z;
      const key = `${level}/${coords.x}/${coords.y}`;
      const paint = (data) => {
        if (data && info.claims && map.hasLayer(this)) paintClaims(canvas, data, coords.x, coords.y, level);
        done(null, canvas);
      };
      if (hitCache.has(key)) {
        setTimeout(() => paint(hitCache.get(key)), 0);
        return canvas;
      }
      const img = new Image();
      img.onload = () => {
        const off = document.createElement('canvas');
        off.width = off.height = tileSize;
        const ctx = off.getContext('2d', { willReadFrequently: true });
        ctx.drawImage(img, 0, 0);
        const data = ctx.getImageData(0, 0, tileSize, tileSize).data;
        hitCache.set(key, data);
        paint(data);
      };
      img.onerror = () => { hitCache.set(key, null); paint(null); };
      img.src = `hits/${current.key}/${level}/${coords.x}/${coords.y}.png`;
      return canvas;
    },
  });

  /** Colours every pixel showing a claimed chunk, with a solid edge where the owner changes. */
  function paintClaims(canvas, data, tx, ty, level) {
    if (claimByChunk.size === 0) return;
    const units = UNITS0 * 2 ** level;
    const upp = units / tileSize;
    const ref = zRef(ty, level);
    const owner = new Int32Array(tileSize * tileSize).fill(-1);
    for (let py = 0; py < tileSize; py++) {
      for (let px = 0; px < tileSize; px++) {
        const i = (py * tileSize + px) * 4;
        if (data[i + 3] !== 255) continue;
        const x = Math.floor(tx * units + (px + 0.5) * upp);
        const z = ref + (data[i] << 8 | data[i + 1]);
        const c = claimByChunk.get(`${Math.floor(x / 16)},${Math.floor(z / 16)}`);
        if (c !== undefined) owner[py * tileSize + px] = c;
      }
    }
    const ctx = canvas.getContext('2d');
    const out = ctx.createImageData(tileSize, tileSize);
    const o = out.data;
    for (let py = 0; py < tileSize; py++) {
      for (let px = 0; px < tileSize; px++) {
        const j = py * tileSize + px;
        const c = owner[j];
        if (c < 0) continue;
        const col = claims[c].rgb;
        const edge = (px > 0 && owner[j - 1] !== c) || (px < tileSize - 1 && owner[j + 1] !== c)
          || (py > 0 && owner[j - tileSize] !== c) || (py < tileSize - 1 && owner[j + tileSize] !== c);
        o[j * 4] = col[0]; o[j * 4 + 1] = col[1]; o[j * 4 + 2] = col[2];
        o[j * 4 + 3] = edge ? 235 : 80;
        const p = claimPositions.get(c) || { u: 0, v: 0, n: 0 };
        p.u += tx * units + (px + 0.5) * upp; p.v += ty * units + (py + 0.5) * upp; p.n++;
        claimPositions.set(c, p);
      }
    }
    ctx.putImageData(out, 0, 0);
    scheduleLabels();
  }

  let labelTimer = null;
  function scheduleLabels() {
    clearTimeout(labelTimer);
    labelTimer = setTimeout(() => {
      labelsLayer.clearLayers();
      for (const [c, p] of claimPositions) {
        if (!claims[c] || p.n === 0) continue;
        L.tooltip({ permanent: true, direction: 'center', className: 'claim-label' })
          .setLatLng(L.latLng(-p.v / p.n, p.u / p.n))
          .setContent(`${escapeHtml(claims[c].name)} · ${escapeHtml(claims[c].owner)}`)
          .addTo(labelsLayer);
      }
      updateLabels();
    }, 250);
  }

  const hitLayer = OBLIQUE ? new HitLayer({
    tileSize, minNativeZoom: NATIVE - zoomOut, maxNativeZoom: NATIVE, minZoom: NATIVE - zoomOut,
    maxZoom: NATIVE + 3, noWrap: true, pane: 'overlayPane', keepBuffer: 2,
  }) : null;

  /** Block under a map point, from the hit maps: {x, y, z} or null. */
  function blockAt(latlng) {
    const u = latlng.lng;
    const v = -latlng.lat;
    if (!OBLIQUE) return { x: Math.floor(u), y: null, z: Math.floor(v) };
    const level = Math.max(0, Math.min(zoomOut, NATIVE - map.getZoom()));
    const units = UNITS0 * 2 ** level;
    const tx = Math.floor(u / units);
    const ty = Math.floor(v / units);
    const data = hitCache.get(`${level}/${tx}/${ty}`);
    if (!data) return null;
    const px = Math.floor((u - tx * units) / units * tileSize);
    const py = Math.floor((v - ty * units) / units * tileSize);
    const i = (py * tileSize + px) * 4;
    if (data[i + 3] !== 255) return null;
    return { x: Math.floor(u), y: current.minY + data[i + 2] * 2, z: zRef(ty, level) + (data[i] << 8 | data[i + 1]) };
  }

  // ------------------------------------------------------------------ layers and controls
  if (info.claims) {
    overlays['Claims'] = OBLIQUE ? hitLayer : vectorClaims;
    overlays['Claims'].addTo(map);
    labelsLayer.addTo(map);
  } else if (OBLIQUE) {
    hitLayer.addTo(map); // still needed for coordinates
  }
  if (info.players) { overlays['Players'] = playersLayer; playersLayer.addTo(map); }
  if (Object.keys(overlays).length) L.control.layers(null, overlays, { collapsed: false }).addTo(map);
  map.on('overlayremove', e => { if (e.layer === overlays['Claims']) map.removeLayer(labelsLayer); });
  map.on('overlayadd', e => { if (e.layer === overlays['Claims']) updateLabels(); });

  const select = document.getElementById('dimension');
  for (const d of info.dimensions) {
    const opt = document.createElement('option');
    opt.value = d.id;
    opt.textContent = d.id.replace(/^minecraft:/, '').replace(/_/g, ' ');
    select.appendChild(opt);
  }

  // URL hash: #<dimension>/<u>/<v>/<zoom>
  const hash = decodeURIComponent(location.hash.slice(1)).split('/');
  current = info.dimensions.find(d => d.id === hash[0]) || info.dimensions[0];
  if (hash.length === 4 && !isNaN(+hash[1])) {
    map.setView(L.latLng(-(+hash[2]), +hash[1]), +hash[3]);
  } else {
    map.setView(toLatLng(info.spawn.x, 64, info.spawn.z), NATIVE);
  }

  function setDimension(dim) {
    current = dim;
    select.value = dim.id;
    if (tiles) map.removeLayer(tiles);
    tiles = new MapTiles('', {
      dim: dim.key, tileSize, minNativeZoom: NATIVE - zoomOut, maxNativeZoom: NATIVE,
      minZoom: NATIVE - zoomOut, maxZoom: NATIVE + 3, noWrap: true, keepBuffer: 2,
    }).addTo(map);
    tiles.bringToBack();
    hitCache.clear();
    indexClaims();
    if (hitLayer) hitLayer.redraw();
    drawVectorClaims();
    drawPlayers();
    updateHash();
  }
  select.addEventListener('change', () => setDimension(info.dimensions.find(d => d.id === select.value)));

  function updateHash() {
    const c = map.getCenter();
    history.replaceState(null, '', `#${encodeURIComponent(current.id)}/${Math.round(c.lng)}/${Math.round(-c.lat)}/${map.getZoom()}`);
  }
  map.on('moveend', updateHash);

  const coords = document.getElementById('coords');
  map.on('mousemove', e => {
    const b = blockAt(e.latlng);
    if (!b) { coords.textContent = '–'; return; }
    let text = b.y === null ? `x ${b.x}, z ${b.z}` : `x ${b.x}, y ${b.y}, z ${b.z}`;
    const c = claimByChunk.get(`${Math.floor(b.x / 16)},${Math.floor(b.z / 16)}`);
    if (c !== undefined) text += ` · ${claims[c].name} (${claims[c].owner})`;
    coords.textContent = text;
  });

  // ------------------------------------------------------------------ claims
  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }
  function hexToRgb(hex) {
    const n = parseInt(hex.slice(1), 16);
    return [n >> 16 & 255, n >> 8 & 255, n & 255];
  }
  function indexClaims() {
    claimByChunk = new Map();
    claimPositions.clear();
    claims.forEach((c, i) => {
      c.rgb = hexToRgb(c.color);
      if (c.dimension !== current.id) return;
      for (const [x, z] of c.chunks) claimByChunk.set(`${x},${z}`, i);
    });
  }
  function drawVectorClaims() {
    if (OBLIQUE) return;
    vectorClaims.clearLayers();
    labelsLayer.clearLayers();
    for (const c of claims) {
      if (c.dimension !== current.id) continue;
      const tip = `<strong>${escapeHtml(c.name)}</strong><br>${escapeHtml(c.owner)}`;
      const squares = c.chunks.map(([x, z]) => [[
        toLatLng(x * 16, 0, z * 16), toLatLng(x * 16 + 16, 0, z * 16),
        toLatLng(x * 16 + 16, 0, z * 16 + 16), toLatLng(x * 16, 0, z * 16 + 16),
      ]]);
      L.polygon(squares, { stroke: false, fillColor: c.color, fillOpacity: 0.25 })
        .bindTooltip(tip, { sticky: true }).addTo(vectorClaims);
      const lines = c.outline.map(([x1, z1, x2, z2]) => [toLatLng(x1, 0, z1), toLatLng(x2, 0, z2)]);
      L.polyline(lines, { color: c.color, weight: 2, opacity: 0.9, interactive: false }).addTo(vectorClaims);
      const cx = c.chunks.reduce((s, [x]) => s + x, 0) / c.chunks.length;
      const cz = c.chunks.reduce((s, [, z]) => s + z, 0) / c.chunks.length;
      const best = c.chunks.reduce((a, b) => (Math.hypot(a[0] - cx, a[1] - cz) <= Math.hypot(b[0] - cx, b[1] - cz) ? a : b));
      L.tooltip({ permanent: true, direction: 'center', className: 'claim-label' })
        .setLatLng(toLatLng(best[0] * 16 + 8, 0, best[1] * 16 + 8))
        .setContent(`${escapeHtml(c.name)} · ${escapeHtml(c.owner)}`)
        .addTo(labelsLayer);
    }
    updateLabels();
  }
  function updateLabels() {
    if (!info.claims) return;
    const show = map.getZoom() >= NATIVE - 1 && map.hasLayer(overlays['Claims']);
    if (show && !map.hasLayer(labelsLayer)) labelsLayer.addTo(map);
    if (!show && map.hasLayer(labelsLayer)) map.removeLayer(labelsLayer);
  }
  map.on('zoomend', updateLabels);
  // Label positions come from the tiles on screen; start again for the new zoom level.
  map.on('zoomstart', () => claimPositions.clear());
  let claimsJson = '';
  async function loadClaims() {
    if (!info.claims) return;
    try {
      const text = await (await fetch('api/claims')).text();
      if (text === claimsJson) return;
      claimsJson = text;
      claims = JSON.parse(text);
      indexClaims();
      if (hitLayer) hitLayer.redraw();
      drawVectorClaims();
    } catch (e) { /* retry later */ }
  }

  // ------------------------------------------------------------------ players
  function drawPlayers() {
    playersLayer.clearLayers();
    for (const p of players) {
      if (p.dimension !== current.id) continue;
      L.circleMarker(toLatLng(p.x, p.y, p.z), { radius: 6, color: '#fff', weight: 2, fillColor: '#3b82f6', fillOpacity: 1 })
        .bindTooltip(escapeHtml(p.name), { permanent: true, direction: 'top', offset: [0, -6], className: 'player-label' })
        .addTo(playersLayer);
    }
  }
  async function loadPlayers() {
    if (!info.players) return;
    try { players = (await (await fetch('api/players')).json()).players; drawPlayers(); } catch (e) { /* retry later */ }
  }

  setDimension(current);
  await loadClaims();
  loadPlayers();
  setInterval(loadClaims, 30000);
  setInterval(loadPlayers, 3000);
  // Refresh tiles from time to time; unchanged tiles answer 304 Not Modified.
  setInterval(() => { if (tiles) tiles.redraw(); hitCache.clear(); if (hitLayer) hitLayer.redraw(); }, 120000);
})();
