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

  // ------------------------------------------------------------------ language
  const TEXT = {
    en: {
      map: 'World Map', claims: 'Claims', players: 'Players', chat: 'Chat', online: 'Online',
      dimension: 'Dimension', joined: 'joined the game', left: 'left the game', noChat: 'No messages yet.',
      nobody: 'Nobody is online.', hide: 'Hide', show: 'Show', spawn: 'Spawn', toSpawn: 'Center the map on the spawn point',
      layers: 'Layers',
    },
    es: {
      map: 'Mapa del mundo', claims: 'Terrenos', players: 'Jugadores', chat: 'Chat', online: 'Conectados',
      dimension: 'Dimensión', joined: 'entró al juego', left: 'salió del juego', noChat: 'Aún no hay mensajes.',
      nobody: 'No hay nadie conectado.', hide: 'Ocultar', show: 'Mostrar', spawn: 'Spawn', toSpawn: 'Centrar el mapa en el punto de aparición',
      layers: 'Capas',
    },
  };
  const LANG = info.language && TEXT[info.language]
    ? info.language
    : ((navigator.languages || [navigator.language || 'en']).map(l => l.slice(0, 2)).find(l => TEXT[l]) || 'en');
  const t = key => TEXT[LANG][key];
  document.documentElement.lang = LANG;
  document.getElementById('dimension').setAttribute('aria-label', t('dimension'));

  document.getElementById('title').textContent = info.name || t('map');
  document.title = info.name || t('map');

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
  const CLAIMS = t('claims');
  const PLAYERS = t('players');

  // ------------------------------------------------------------------ state
  let current = null;
  let tiles = null;
  let claims = [];
  let claimByChunk = new Map(); // "cx,cz" -> claim index, current dimension
  let players = [];
  const hitCache = new Map(); // "level/x/y" -> Uint8ClampedArray (RGBA) or null

  // What the visitor chose to see (only for layers the server publishes), remembered in this browser.
  const shown = { claims: true, players: true, chat: true };
  try { Object.assign(shown, JSON.parse(localStorage.getItem('webmap.layers') || '{}')); } catch (e) { /* ignore */ }
  const saveShown = () => { try { localStorage.setItem('webmap.layers', JSON.stringify(shown)); } catch (e) { /* ignore */ } };
  const claimsOn = () => info.claims && shown.claims;

  const vectorClaims = L.layerGroup();
  const labelsLayer = L.layerGroup();
  const playersLayer = L.layerGroup();

  // ------------------------------------------------------------------ hit maps (oblique)
  const zRef = (ty, level) => Math.floor((ty * UNITS0 * 2 ** level + current.minY * proj.cos) / proj.sin) - 1;
  const claimPositions = new Map(); // claim index -> {u, v, n} gathered from the tiles drawn

  const HitLayer = L.GridLayer.extend({
    createTile(coords, done) {
      const canvas = L.DomUtil.create('canvas');
      canvas.width = canvas.height = tileSize;
      this.loadHits(canvas, coords, done, null);
      return canvas;
    },
    /** Fetches (or reuses) the hit map of a tile and paints claims over it. */
    loadHits(canvas, coords, done, version) {
      const level = NATIVE - coords.z;
      const key = `${level}/${coords.x}/${coords.y}`;
      const paint = (data) => {
        canvas.getContext('2d').clearRect(0, 0, tileSize, tileSize);
        if (data && claimsOn()) paintClaims(canvas, data, coords.x, coords.y, level);
        done(null, canvas);
      };
      if (version === null && hitCache.has(key)) {
        setTimeout(() => paint(hitCache.get(key)), 0);
        return;
      }
      const dim = current.key;
      const img = new Image();
      img.onload = () => {
        if (dim !== current.key) return;
        const off = document.createElement('canvas');
        off.width = off.height = tileSize;
        const ctx = off.getContext('2d', { willReadFrequently: true });
        ctx.drawImage(img, 0, 0);
        const data = ctx.getImageData(0, 0, tileSize, tileSize).data;
        // A missing hit map is answered with a 1x1 transparent image: keep "nothing here".
        const value = img.naturalWidth === tileSize ? data : null;
        hitCache.set(key, value);
        paint(value);
      };
      img.onerror = () => { hitCache.set(key, null); paint(null); };
      img.src = `hits/${dim}/${level}/${coords.x}/${coords.y}.png` + (version === null ? '' : `?v=${version}`);
    },
    /** Reloads one tile in place after the server re-rendered it. */
    refreshTile(level, x, y, version) {
      const z = NATIVE - level;
      const tile = this._tiles[`${x}:${y}:${z}`];
      hitCache.delete(`${level}/${x}/${y}`);
      if (tile) this.loadHits(tile.el, { x, y, z }, () => {}, version);
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
      if (!claimsOn()) return;
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
  // The hit maps are always loaded (coordinates under the mouse); claims are painted on them when shown.
  if (hitLayer) hitLayer.addTo(map);
  if (claimsOn() && !OBLIQUE) vectorClaims.addTo(map);
  if (info.players && shown.players) playersLayer.addTo(map);

  // Layer check boxes: one per layer the server publishes.
  const layersPanel = document.getElementById('layers');
  function addCheck(key, label, apply) {
    const row = document.createElement('label');
    const box = document.createElement('input');
    box.type = 'checkbox';
    box.checked = shown[key];
    box.addEventListener('change', () => { shown[key] = box.checked; saveShown(); apply(box.checked); });
    row.append(box, ' ', label);
    layersPanel.appendChild(row);
  }
  if (info.claims) {
    addCheck('claims', CLAIMS, on => {
      if (!OBLIQUE) { if (on) vectorClaims.addTo(map); else map.removeLayer(vectorClaims); }
      if (hitLayer) { claimPositions.clear(); repaintHits(); }
      if (!on) labelsLayer.clearLayers();
      if (on) drawVectorClaims();
      updateLabels();
    });
  }
  if (info.players) {
    addCheck('players', PLAYERS, on => { if (on) playersLayer.addTo(map); else map.removeLayer(playersLayer); });
  }
  if (info.chat) addCheck('chat', t('chat'), () => updateSideTabs());
  if (layersPanel.children.length) {
    const head = document.createElement('strong');
    head.textContent = t('layers');
    layersPanel.prepend(head);
    layersPanel.hidden = false;
    L.DomEvent.disableClickPropagation(layersPanel);
  }

  /** Repaints the claims on the hit tiles already loaded, without fetching them again. */
  function repaintHits() {
    for (const key in hitLayer._tiles) {
      const tile = hitLayer._tiles[key];
      const { x, y, z } = tile.coords;
      const data = hitCache.get(`${NATIVE - z}/${x}/${y}`);
      const ctx = tile.el.getContext('2d');
      ctx.clearRect(0, 0, tileSize, tileSize);
      if (data && claimsOn()) paintClaims(tile.el, data, x, y, NATIVE - z);
    }
  }

  const select = document.getElementById('dimension');
  for (const d of info.dimensions) {
    const opt = document.createElement('option');
    opt.value = d.id;
    opt.textContent = d.id.replace(/^minecraft:/, '').replace(/_/g, ' ');
    select.appendChild(opt);
  }

  // Spawn point: a marker in the overworld and a button that centres the map on it.
  const spawnDim = info.dimensions.find(d => d.id === 'minecraft:overworld') || info.dimensions[0];
  const spawnY = info.spawn.y === undefined ? 64 : info.spawn.y;
  const spawnLayer = L.layerGroup();
  L.circleMarker(toLatLng(info.spawn.x, spawnY, info.spawn.z), {
    radius: 5, color: '#111', weight: 2, fillColor: '#facc15', fillOpacity: 1,
  }).bindTooltip(t('spawn'), { direction: 'top', offset: [0, -5] }).addTo(spawnLayer);
  function goToSpawn() {
    if (current !== spawnDim) setDimension(spawnDim);
    map.setView(toLatLng(info.spawn.x, spawnY, info.spawn.z), Math.max(map.getZoom(), NATIVE - 1));
  }
  const spawnButton = document.getElementById('spawn');
  spawnButton.textContent = `⌂ ${t('spawn')}`;
  spawnButton.title = t('toSpawn');
  spawnButton.addEventListener('click', goToSpawn);

  // URL hash: #<dimension>/<u>/<v>/<zoom>
  const hash = decodeURIComponent(location.hash.slice(1)).split('/');
  current = info.dimensions.find(d => d.id === hash[0]) || info.dimensions[0];
  if (hash.length === 4 && !isNaN(+hash[1])) {
    map.setView(L.latLng(-(+hash[2]), +hash[1]), +hash[3]);
  } else {
    map.setView(toLatLng(info.spawn.x, spawnY, info.spawn.z), NATIVE);
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
    if (dim === spawnDim) spawnLayer.addTo(map); else map.removeLayer(spawnLayer);
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
    const c = claimsOn() ? claimByChunk.get(`${Math.floor(b.x / 16)},${Math.floor(b.z / 16)}`) : undefined;
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
    if (!claimsOn()) return;
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
    const show = map.getZoom() >= NATIVE - 1 && claimsOn();
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
  let online = [];
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
    if (!info.players && !info.online) return;
    try {
      const r = await (await fetch('api/players')).json();
      players = r.players || [];
      drawPlayers();
      if (info.online) { online = r.online || []; drawOnline(r.max || 0); }
    } catch (e) { /* retry later */ }
  }
  function flyTo(p) {
    const dim = info.dimensions.find(d => d.id === p.dimension);
    if (!dim) return;
    if (dim !== current) setDimension(dim);
    map.setView(toLatLng(p.x, p.y, p.z), Math.max(map.getZoom(), NATIVE));
  }

  // ------------------------------------------------------------------ side panel (chat, online list)
  const side = document.getElementById('side');
  const tabs = document.getElementById('side-tabs');
  const chatList = document.getElementById('chat-list');
  const onlineList = document.getElementById('online-list');
  const panes = [];
  function addTab(id, label, pane) {
    const b = document.createElement('button');
    b.type = 'button';
    b.setAttribute('role', 'tab');
    b.dataset.tab = id;
    b.textContent = label;
    b.addEventListener('click', () => { side.classList.remove('collapsed'); selectTab(id); });
    tabs.appendChild(b);
    panes.push({ id, button: b, pane });
  }
  function selectTab(id) {
    for (const p of panes) {
      p.button.setAttribute('aria-selected', String(p.id === id));
      p.pane.hidden = p.id !== id;
    }
    try { localStorage.setItem('webmap.tab', id); } catch (e) { /* ignore */ }
    if (id === 'chat') chatList.parentElement.scrollTop = chatList.parentElement.scrollHeight;
  }
  if (info.chat) addTab('chat', t('chat'), chatList);
  if (info.online) addTab('online', t('online'), onlineList);
  /** Shows the tabs the visitor wants (the chat can be hidden with its check box). */
  function updateSideTabs() {
    let visible = 0;
    for (const p of panes) {
      const on = p.id !== 'chat' || shown.chat;
      p.button.hidden = !on;
      if (on) visible++;
    }
    side.hidden = visible === 0;
    const selected = panes.find(p => p.button.getAttribute('aria-selected') === 'true');
    if (!selected || selected.button.hidden) {
      const first = panes.find(p => !p.button.hidden);
      if (first) selectTab(first.id);
    }
  }
  if (panes.length) {
    const toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.className = 'toggle';
    const setToggle = () => {
      const collapsed = side.classList.contains('collapsed');
      toggle.textContent = collapsed ? '▴' : '▾';
      toggle.title = collapsed ? t('show') : t('hide');
    };
    toggle.addEventListener('click', () => {
      side.classList.toggle('collapsed');
      setToggle();
      try { localStorage.setItem('webmap.collapsed', side.classList.contains('collapsed') ? '1' : ''); } catch (e) { /* ignore */ }
    });
    tabs.appendChild(toggle);
    let saved = null;
    try {
      saved = localStorage.getItem('webmap.tab');
      if (localStorage.getItem('webmap.collapsed')) side.classList.add('collapsed');
    } catch (e) { /* ignore */ }
    selectTab(panes.some(p => p.id === saved) ? saved : panes[0].id);
    setToggle();
    side.hidden = false;
    updateSideTabs();
    L.DomEvent.disableClickPropagation(side);
    L.DomEvent.disableScrollPropagation(side);
  }

  function drawOnline(max) {
    const tab = panes.find(p => p.id === 'online');
    if (tab) tab.button.textContent = `${t('online')} (${online.length}${max ? '/' + max : ''})`;
    onlineList.replaceChildren();
    if (online.length === 0) {
      const li = document.createElement('li');
      li.className = 'empty';
      li.textContent = t('nobody');
      onlineList.appendChild(li);
      return;
    }
    for (const name of [...online].sort((a, b) => a.localeCompare(b))) {
      const li = document.createElement('li');
      li.textContent = name;
      const pos = players.find(p => p.name === name);
      if (pos) {
        li.className = 'located';
        li.addEventListener('click', () => flyTo(pos));
      }
      onlineList.appendChild(li);
    }
  }

  let chatSince = 0;
  const timeFormat = new Intl.DateTimeFormat(LANG, { hour: '2-digit', minute: '2-digit' });
  function addChat(m) {
    const li = document.createElement('li');
    const time = document.createElement('span');
    time.className = 'time';
    time.textContent = timeFormat.format(new Date(m.time));
    li.appendChild(time);
    if (m.kind === 'chat') {
      const name = document.createElement('span');
      name.className = 'name';
      name.textContent = `<${m.name}> `;
      li.appendChild(name);
      li.appendChild(document.createTextNode(m.text));
    } else {
      const ev = document.createElement('span');
      ev.className = 'event';
      ev.textContent = `${m.name} ${t(m.kind === 'join' ? 'joined' : 'left')}`;
      li.appendChild(ev);
    }
    chatList.appendChild(li);
  }
  async function loadChat() {
    if (!info.chat) return;
    try {
      const r = await (await fetch(`api/chat?since=${chatSince}`)).json();
      if (r.last < chatSince) { chatSince = 0; chatList.replaceChildren(); return; } // server restarted
      const box = chatList.parentElement;
      const atBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 20;
      chatList.querySelector('.empty')?.remove();
      for (const m of r.messages) addChat(m);
      chatSince = r.last;
      while (chatList.children.length > 300) chatList.firstChild.remove();
      if (chatList.children.length === 0) {
        const li = document.createElement('li');
        li.className = 'empty';
        li.textContent = t('noChat');
        chatList.appendChild(li);
      }
      if (atBottom) box.scrollTop = box.scrollHeight;
    } catch (e) { /* retry later */ }
  }

  // ------------------------------------------------------------------ live tile updates
  // The server lists the tiles it wrote; only those are fetched again, in place, without flicker.
  let changeSeq = -1;
  async function loadChanges() {
    try {
      const r = await (await fetch(`api/changes?since=${changeSeq}`)).json();
      const first = changeSeq < 0;
      changeSeq = r.seq;
      if (first) return;
      if (r.reset) {
        hitCache.clear();
        if (tiles) tiles.redraw();
        if (hitLayer) hitLayer.redraw();
        return;
      }
      for (const [dim, level, x, y] of r.tiles) {
        if (dim !== current.key) continue;
        const tile = tiles && tiles._tiles[`${x}:${y}:${NATIVE - level}`];
        if (tile) tile.el.src = `tiles/${dim}/${level}/${x}/${y}.png?v=${r.seq}`;
        if (hitLayer) hitLayer.refreshTile(level, x, y, r.seq);
      }
    } catch (e) { /* retry later */ }
  }

  // The page follows config changes (title, layers, chat...) by reloading itself.
  async function checkInfo() {
    try {
      const fresh = await (await fetch('api/info')).json();
      if (fresh.revision !== info.revision) location.reload();
    } catch (e) { /* server restarting */ }
  }

  setDimension(current);
  await loadClaims();
  loadPlayers();
  loadChat();
  loadChanges();
  setInterval(loadClaims, 30000);
  setInterval(loadPlayers, 3000);
  setInterval(loadChat, 3000);
  setInterval(loadChanges, 5000);
  setInterval(checkInfo, 30000);
})();
