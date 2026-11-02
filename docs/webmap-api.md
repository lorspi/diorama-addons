# Web map: HTTP API

The web map is served by the game server itself (default `http://<server>:8123/`). Everything is
read-only: only `GET` and `HEAD` are accepted (`405` otherwise). There is no authentication in this
version, so nothing sensitive is published: claim members and permissions are never exposed, and player
positions are off unless the server enables them.

Configuration: `config/dioaddons_webmap-common.toml` (`http.*`, `web.*`, `render.*`, `layers.*`). The `http`,
`web` and `layers` sections apply while the server runs: NeoForge reloads the file when it is saved, and
`/webmap reload` reads it by hand (NeoForge's file watcher gets no events on some file systems, such as
Windows folders under WSL or network drives). A changed port, bind address or thread count restarts the web
server only. `render.*` needs a server restart. Open pages poll `/api/info` and reload when its `revision`
changes.

## `GET /`

The web page (Leaflet 1.9.4, bundled in the jar; no external requests). Static files: `/index.html`,
`/app.js`, `/style.css`, `/vendor/leaflet/...`.

`GET /favicon` (and `/favicon.ico`) answers the icon set in `web.favicon`: a `302` redirect for an http(s)
URL, or the file (png, ico, svg, gif, jpg, webp, at most 1 MB; any other file is refused and logged). Without
a setting: the server's `server-icon.png`, or the mod's own icon. The page `<title>` is `web.title` (empty =
the server MOTD without formatting codes).

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

Responses carry an `ETag`; send `If-None-Match` to get `304 Not Modified`. Tiles that do not exist (nothing
generated there, or not rendered yet) answer `200` with a 1×1 transparent PNG, so browsers log no errors.

## `GET /hits/{dim}/{zoom}/{x}/{y}.png` (Diorama style)

Same tile grid as `/tiles`. For every pixel, the block it shows: alpha 255 when something is there, red and
green = `z − zRef` (16 bits), blue = `(y − minY) / 2`, where
`zRef = floor((y_tile · units + minY · cos) / sin) − 1` and `units = 128 · 2^zoom`.

## `GET /api/info`

```json
{
  "revision": 9286585572891,
  "name": "A Minecraft Server",
  "language": "auto",
  "minecraftVersion": "1.21.1",
  "tileSize": 512,
  "projection": {"type": "oblique", "sin": 0.766, "cos": 0.643, "tileUnits": 128, "pixelsPerUnit": 4},
  "zoomOutLevels": 4,
  "claims": true,
  "players": false,
  "online": true,
  "chat": false,
  "dimensions": [{"id": "minecraft:overworld", "key": "minecraft~overworld", "minY": -64}],
  "spawn": {"x": -16, "y": 72, "z": 16}
}
```

`claims` is true when the claims layer is enabled and the claims mod is installed. `players`, `online` and
`chat` mirror `layers.showPlayers`, `layers.showOnlineList` and `layers.showChat`. `name` is the page title,
`language` is `web.language` (`auto`, `en`, `es`). `revision` changes whenever the config is applied again.

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
{
  "enabled": true,
  "players": [{"name": "Alice", "dimension": "minecraft:overworld", "x": -23, "y": 56, "z": -3, "yaw": 90}],
  "onlineEnabled": true,
  "online": ["Alice", "Bob"],
  "max": 20
}
```

- `players` (positions) is filled only with `layers.showPlayers = true` (default off). Spectators never get a
  position.
- `online` (names of every connected player) is filled only with `layers.showOnlineList = true` (default
  on); `max` is the server's player limit.

Refreshed every `layers.playerUpdateSeconds`.

## `GET /api/chat?since={id}`

```json
{"enabled": true, "last": 42, "messages": [
  {"id": 41, "time": 1793636314587, "kind": "join", "name": "Alice", "text": ""},
  {"id": 42, "time": 1793636404120, "kind": "chat", "name": "Alice", "text": "hola"}
]}
```

Public chat messages (`kind = "chat"`) and, with `layers.chatJoinLeave`, joins and leaves, kept in memory only
(the last `layers.chatHistory`). `since` returns the messages after that id (`0` = everything kept); `last` is
the newest id, so a `last` smaller than the `since` you sent means the server restarted. Messages cancelled by
other mods are not recorded. With `layers.showChat = false` (default): `{"enabled": false, ...}` with no
messages, and nothing is recorded.

## `GET /api/changes?since={seq}`

```json
{"seq": 120, "reset": false, "tiles": [["minecraft~overworld", 0, -2, -1], ["minecraft~overworld", 1, -1, -1]]}
```

Tiles written or removed since sequence `seq`, as `[dim, zoom, x, y]`, so a page reloads only those. The first
call (`since=-1`) returns only the current `seq`. `reset: true` means the client is too far behind (the log
keeps the last 8192 changes) or the cache was cleared: reload every tile. The page polls every 5 seconds.

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
| `/webmap reload` | Read the config file again and apply the `http`, `web` and `layers` sections |
| `/webmap restart` | Restart the web server (not the game server) with the current `http` settings |
