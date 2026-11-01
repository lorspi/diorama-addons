// Diorama Addons web map. Map units are blocks: lng = x, lat = -z (north is up).
(async function () {
  'use strict';
  const info = await (await fetch('api/info')).json();
  const zoomOut = info.zoomOutLevels;
  document.getElementById('title').textContent = info.name || 'World Map';
  document.title = info.name || 'World Map';

  const map = L.map('map', {
    crs: L.CRS.Simple,
    minZoom: -zoomOut,
    maxZoom: 3,
    zoomSnap: 1,
    attributionControl: false,
  });

  const toLatLng = (x, z) => L.latLng(-z, x);

  const MapTiles = L.TileLayer.extend({
    getTileUrl(coords) {
      return `tiles/${this.options.dim}/${-coords.z}/${coords.x}/${coords.y}.png`;
    },
  });

  let tiles = null;
  const claimsLayer = L.layerGroup();
  const labelsLayer = L.layerGroup();
  const playersLayer = L.layerGroup();
  const overlays = {};
  if (info.claims) { overlays['Claims'] = claimsLayer; claimsLayer.addTo(map); labelsLayer.addTo(map); }
  if (info.players) { overlays['Players'] = playersLayer; playersLayer.addTo(map); }
  if (Object.keys(overlays).length) L.control.layers(null, overlays, { collapsed: false }).addTo(map);
  map.on('overlayremove', e => { if (e.layer === claimsLayer) map.removeLayer(labelsLayer); });
  map.on('overlayadd', e => { if (e.layer === claimsLayer) labelsLayer.addTo(map); });

  const select = document.getElementById('dimension');
  for (const d of info.dimensions) {
    const opt = document.createElement('option');
    opt.value = d.id;
    opt.textContent = d.id.replace(/^minecraft:/, '').replace(/_/g, ' ');
    select.appendChild(opt);
  }

  // URL hash: #<dimension>/<x>/<z>/<zoom>
  const hash = decodeURIComponent(location.hash.slice(1)).split('/');
  let current = info.dimensions.find(d => d.id === hash[0]) || info.dimensions[0];
  if (hash.length === 4 && !isNaN(+hash[1])) {
    map.setView(toLatLng(+hash[1], +hash[2]), +hash[3]);
  } else {
    map.setView(toLatLng(info.spawn.x, info.spawn.z), 0);
  }

  function setDimension(dim) {
    current = dim;
    select.value = dim.id;
    if (tiles) map.removeLayer(tiles);
    tiles = new MapTiles('', {
      dim: dim.key, tileSize: info.tileSize, minNativeZoom: -zoomOut, maxNativeZoom: 0,
      minZoom: -zoomOut, maxZoom: 3, noWrap: true, keepBuffer: 2,
    }).addTo(map);
    tiles.bringToBack();
    drawClaims();
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
    coords.textContent = `x ${Math.floor(e.latlng.lng)}, z ${Math.floor(-e.latlng.lat)}`;
  });

  // ---------------------------------------------------------------- claims
  let claims = [];
  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }
  function drawClaims() {
    claimsLayer.clearLayers();
    labelsLayer.clearLayers();
    for (const c of claims) {
      if (c.dimension !== current.id) continue;
      const tip = `<strong>${escapeHtml(c.name)}</strong><br>${escapeHtml(c.owner)}`;
      const squares = c.chunks.map(([x, z]) => [[
        toLatLng(x * 16, z * 16), toLatLng(x * 16 + 16, z * 16),
        toLatLng(x * 16 + 16, z * 16 + 16), toLatLng(x * 16, z * 16 + 16),
      ]]);
      L.polygon(squares, { stroke: false, fillColor: c.color, fillOpacity: 0.25 })
        .bindTooltip(tip, { sticky: true }).addTo(claimsLayer);
      const lines = c.outline.map(([x1, z1, x2, z2]) => [toLatLng(x1, z1), toLatLng(x2, z2)]);
      L.polyline(lines, { color: c.color, weight: 2, opacity: 0.9, interactive: false }).addTo(claimsLayer);
      // Label at the chunk closest to the centre of the claim.
      const cx = c.chunks.reduce((s, [x]) => s + x, 0) / c.chunks.length;
      const cz = c.chunks.reduce((s, [, z]) => s + z, 0) / c.chunks.length;
      const best = c.chunks.reduce((a, b) => (Math.hypot(a[0] - cx, a[1] - cz) <= Math.hypot(b[0] - cx, b[1] - cz) ? a : b));
      L.tooltip({ permanent: true, direction: 'center', className: 'claim-label' })
        .setLatLng(toLatLng(best[0] * 16 + 8, best[1] * 16 + 8))
        .setContent(`${escapeHtml(c.name)} · ${escapeHtml(c.owner)}`)
        .addTo(labelsLayer);
    }
    updateLabels();
  }
  function updateLabels() {
    if (!info.claims) return;
    const show = map.getZoom() >= -1 && map.hasLayer(claimsLayer);
    if (show && !map.hasLayer(labelsLayer)) labelsLayer.addTo(map);
    if (!show && map.hasLayer(labelsLayer)) map.removeLayer(labelsLayer);
  }
  map.on('zoomend', updateLabels);
  async function loadClaims() {
    if (!info.claims) return;
    try { claims = await (await fetch('api/claims')).json(); drawClaims(); } catch (e) { /* retry later */ }
  }

  // ---------------------------------------------------------------- players
  let players = [];
  function drawPlayers() {
    playersLayer.clearLayers();
    for (const p of players) {
      if (p.dimension !== current.id) continue;
      L.circleMarker(toLatLng(p.x, p.z), { radius: 6, color: '#fff', weight: 2, fillColor: '#3b82f6', fillOpacity: 1 })
        .bindTooltip(escapeHtml(p.name), { permanent: true, direction: 'top', offset: [0, -6], className: 'player-label' })
        .addTo(playersLayer);
    }
  }
  async function loadPlayers() {
    if (!info.players) return;
    try { players = (await (await fetch('api/players')).json()).players; drawPlayers(); } catch (e) { /* retry later */ }
  }

  setDimension(current);
  loadClaims();
  loadPlayers();
  setInterval(loadClaims, 30000);
  setInterval(loadPlayers, 3000);
  // Refresh tiles from time to time; unchanged tiles answer 304 Not Modified.
  setInterval(() => tiles && tiles.redraw(), 120000);
})();
