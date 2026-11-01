# Web map: HTTP API

The web map is served by the game server itself (default `http://<server>:8123/`). Everything is
read-only: only `GET` and `HEAD` are accepted (`405` otherwise). There is no authentication in this
version, so nothing sensitive is published: claim members and permissions are never exposed, and player
positions are off unless the server enables them.

Configuration: `config/dioaddons_webmap-common.toml` (`http.port`, `http.bind`, `render.*`, `layers.*`).

## `GET /`

The web page (Leaflet 1.9.4, bundled in the jar; no external requests). Static files: `/index.html`,
`/app.js`, `/style.css`, `/vendor/leaflet/...`.

The page state is kept in the URL hash: `#<dimension>/<x>/<z>/<zoom>`, for example
`#minecraft%3Aoverworld/120/-340/0`.

## `GET /tiles/{dim}/{zoom}/{x}/{y}.png`

| Part | Meaning |
|---|---|
| `dim` | Dimension key: the dimension id with `:` replaced by `~` and `/` by `+` (`minecraft~overworld`) |
| `zoom` | `0` = 1 pixel per block. `k > 0` = zoomed out, each tile covers `512 · 2^k` blocks |
| `x`, `y` | Tile index: `floor(blockX / (512 · 2^zoom))`, `floor(blockZ / (512 · 2^zoom))` |

Tiles are 512×512 PNG with transparency where nothing is generated. At zoom 0 one tile is exactly one
region file (32×32 chunks). Responses carry an `ETag`; send `If-None-Match` to get `304 Not Modified`.
Missing tiles answer `404`.

## `GET /api/info`

```json
{
  "name": "A Minecraft Server",
  "minecraftVersion": "1.21.1",
  "tileSize": 512,
  "zoomOutLevels": 4,
  "claims": true,
  "players": false,
  "dimensions": [{"id": "minecraft:overworld", "key": "minecraft~overworld"}],
  "spawn": {"x": -16, "z": 16}
}
```

`claims` is true when the claims layer is enabled and the claims mod is installed. `players` mirrors
`layers.showPlayers`.

## `GET /api/claims`

```json
[
  {
    "id": "6f1c…",
    "name": "Alice Farm",
    "owner": "Alice",
    "color": "#f2613d",
    "dimension": "minecraft:overworld",
    "chunks": [[-3, -2], [-2, -2], [-2, -1]],
    "outline": [[-48, -32, -16, -32], [-32, -16, -48, -16]]
  }
]
```

- `chunks`: chunk coordinates `[x, z]`.
- `outline`: border segments `[x1, z1, x2, z2]` in block coordinates, merged where possible.
- `color`: stable colour derived from the owner's UUID.

An empty array when the claims mod is not installed or `layers.showClaims = false`.

## `GET /api/players`

```json
{"enabled": true, "players": [{"name": "Alice", "dimension": "minecraft:overworld", "x": -23, "y": 56, "z": -3}]}
```

With `layers.showPlayers = false` (default): `{"enabled": false, "players": []}`. Spectators are never listed.
Positions are refreshed every `layers.playerUpdateSeconds`.

## Rendering

- Tiles are rendered on a single background thread with minimum priority, limited to
  `render.maxChunksPerSecond`. Chunks are read through the server's chunk storage (`ChunkMap.read`), which is
  asynchronous and includes data not yet flushed to disk; the server thread is never used.
- Only fully generated chunks are drawn. Style: top-down, vanilla map colours, relief shading against the
  northern neighbour, darker deep water.
- Changed chunks are collected from `ChunkDataEvent.Save` and `ChunkEvent.Unload` and re-rendered every
  `render.scanIntervalSeconds`; only those chunks are redrawn inside their tile, then the zoomed-out tiles
  above them.
- On start (`render.renderMissingOnStart`), regions without a tile or with a region file newer than the tile
  are queued.
- Cache: `<world>/dioaddons_webmap/tiles/<dim>/<zoom>/<x>_<y>.png`.

## Commands (operators)

| Command | Effect |
|---|---|
| `/webmap` or `/webmap status` | Web server state, render queue, counters, layers |
| `/webmap render [dimension]` | Re-render every region (of one dimension) |
| `/webmap clearcache` | Delete all tiles and render everything again |
