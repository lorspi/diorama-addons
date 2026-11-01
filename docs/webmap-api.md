# Web map: HTTP API

The web map is served by the game server itself (default `http://<server>:8123/`). Everything is
read-only: only `GET` and `HEAD` are accepted (`405` otherwise). There is no authentication in this
version, so nothing sensitive is published: claim members and permissions are never exposed, and player
positions are off unless the server enables them.

Configuration: `config/dioaddons_webmap-common.toml` (`http.port`, `http.bind`, `render.*`, `layers.*`).

## `GET /`

The web page (Leaflet 1.9.4, bundled in the jar; no external requests). Static files: `/index.html`,
`/app.js`, `/style.css`, `/vendor/leaflet/...`.

The page state is kept in the URL hash: `#<dimension>/<u>/<v>/<zoom>` in map units (top-down style:
`u = x`, `v = z`), for example `#minecraft%3Aoverworld/120/-340/2`.

## `GET /tiles/{dim}/{zoom}/{x}/{y}.png`

| Part | Meaning |
|---|---|
| `dim` | Dimension key: the dimension id with `:` replaced by `~` and `/` by `+` (`minecraft~overworld`) |
| `zoom` | `0` = most detail. `k > 0` = zoomed out, each tile covers `2^k` times more |
| `x`, `y` | Tile index, see below |

Tiles are 512×512 PNG with transparency where nothing is generated.

- **Diorama style** (`projection.type = "oblique"`): map space `u = x`, `v = z·sin − y·cos` (camera looking
  north and down; `sin`, `cos` in `/api/info`). A tile covers `128 · 2^zoom` units; `x = floor(u / units)`,
  `y = floor(v / units)`. 4 pixels per unit at zoom 0.
- **Top-down style** (`projection.type = "topdown"`): 1 pixel per block at zoom 0, a tile covers
  `512 · 2^zoom` blocks; `x = floor(blockX / size)`, `y = floor(blockZ / size)`. One zoom-0 tile is one
  region file.

Responses carry an `ETag`; send `If-None-Match` to get `304 Not Modified`. Missing tiles answer `404`.

## `GET /hits/{dim}/{zoom}/{x}/{y}.png` (Diorama style)

Same tile grid as `/tiles`. For every pixel, the block it shows: alpha 255 when something is there, red and
green = `z − zRef` (16 bits), blue = `(y − minY) / 2`, where
`zRef = floor((y_tile · units + minY · cos) / sin) − 1` and `units = 128 · 2^zoom`.

## `GET /api/info`

```json
{
  "name": "A Minecraft Server",
  "minecraftVersion": "1.21.1",
  "tileSize": 512,
  "projection": {"type": "oblique", "sin": 0.766, "cos": 0.643, "tileUnits": 128, "pixelsPerUnit": 4},
  "zoomOutLevels": 4,
  "claims": true,
  "players": false,
  "dimensions": [{"id": "minecraft:overworld", "key": "minecraft~overworld", "minY": -64}],
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
- Only fully generated chunks are drawn. Diorama style: angled view from block textures with the Diorama
  light model. Top-down style: vanilla map colours with relief shading.
- Changed chunks are collected from `ChunkDataEvent.Save` and `ChunkEvent.Unload` and redrawn every
  `render.scanIntervalSeconds` (top-down: only those chunks inside their tile; Diorama style: the tiles
  that can show them), then the zoomed-out tiles above them.
- On start (`render.renderMissingOnStart`), regions without a tile or with a region file newer than the tile
  are queued.
- Cache: `<world>/dioaddons_webmap/diorama/{terrain,hits}/<dim>/<zoom>/<x>_<y>.png` (Diorama style) or
  `<world>/dioaddons_webmap/tiles/<dim>/<zoom>/<x>_<y>.png` (top-down).
- Vanilla textures (Diorama style): `<server>/dioaddons_webmap/minecraft-client-<version>.jar`.

## Commands (operators)

| Command | Effect |
|---|---|
| `/webmap` or `/webmap status` | Web server state, render queue, counters, layers |
| `/webmap render [dimension]` | Re-render every region (of one dimension) |
| `/webmap clearcache` | Delete all tiles and render everything again |
