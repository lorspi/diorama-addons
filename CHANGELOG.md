# Changelog

Each mod is versioned on its own with [Semantic Versioning](https://semver.org/). Releases are tagged
`<module>-v<version>`, for example `claims-v0.1.0`. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## claims-api

### [Unreleased]

### [0.1.0] - 2026-11-01

- First release: read-only API to query claims (`ClaimsApi`, `ClaimInfo`, `ChunkCoord`), change listener and
  `ClaimsApiProvider`. Bundled (Jar-in-Jar) by the other mods.

## render-core

### [Unreleased]

### [0.1.0] - 2026-11-01

- New library: terrain shading in the style of the Diorama map, top-down (claims screen) and angled
  (web map). Bundled by the mods that use it.

## claims

### [Unreleased]

### [0.3.0] - 2026-11-03

- **Claim settings tab** in the claims screen: a welcome and a farewell notification for whoever enters or leaves
  the claim (title and message, with `{player}`, `{claim}`, `{owner}` and colour codes such as `&a` or `&#FF8800`;
  without a title the claim's name is used) and a **visitor alarm** that tells the owner and/or the members when
  someone who is neither comes in (chat message and bell, at most once per visitor every
  `greetings.alarmCooldownSeconds`). Sent with vanilla packets, so players without the mod see them too; shown as
  title, action bar or chat (`greetings.display`). Same from commands: `/claims settings ...`.
- **Players' own limits**: operators can give a player more or fewer chunks, claims or members per claim than the
  server limits with `/claims admin limits <player> <chunks|claims|members> <n|default>` (or `reset`). The
  screen and `/claims list` show each player's own limits.
- Storage format 2 (claim settings and players' limits; version 1 data loads as is). Network protocol 3: clients
  and servers must both run 0.3.x to use the screen.

### [0.2.0] - 2026-11-02

- **Admin mode in the claims screen** (operators): a button turns it on and off (same as
  `/claims admin bypass`). It lists every claim on the server, searchable by claim or owner name, and lets
  operators rename, delete, edit members and public permissions, add or release chunks, teleport to a claim
  (**Go**) and transfer it to another player. Other players' claims are drawn in orange, the panel gets an
  orange frame and a "⚠ Editing X's claim" banner.
- New `/claims admin transfer <player>`; `/claims help` lists the admin commands to operators.
- The result of an action stays visible in the screen for a few seconds.
- Network protocol version 2: clients and servers must both run 0.2.x to use the screen.

### [0.1.0] - 2026-11-01

- Chunk-based claims with per-player limits (chunks and claims), contiguity and optional spawn distance.
- Friends with per-member permissions and templates (Visitor, Member, Trusted); public permissions and
  claim settings (PvP, explosions, fire spread, mob griefing).
- Claims screen: the real terrain around you in the style of the Diorama map (texture colours, biome tints,
  sun and shadows, relief) under a chunk grid with claim colours and borders; drag selection, claim list,
  members and public tabs (key **G** or `/claims menu`).
- Commands for players without the client mod: `/claim`, `/unclaim`, `/claims ...`, `/claims admin ...`.
- Protection for blocks, containers, items, entities, animals, farmland, explosions, fire, fluids, pistons
  and mob griefing.
- Messages translated on the server (English, Spanish), so vanilla clients read them too.
- Versioned storage; game tests for protection and persistence.

## webmap

### [Unreleased]

### [0.3.0] - 2026-11-02

- Terrain that is loaded in the game but not saved yet (newly explored or edited) appears on the map within
  seconds: loaded chunks are checked in small slices on the server thread and changed ones are rendered from a
  snapshot (`render.liveCheckSeconds`, `render.liveBudgetMicros`). Before, only saved chunks were drawn
  (autosave every five minutes).
- Zoomed-out levels are refreshed every 15 s while level 0 is still rendering, instead of only when the queue
  is empty, so zooming out no longer shows black while players explore.
- Land claims and the public chat from Mochos Esenciales are shown too (claims by reflection, chat through an
  optional mixin; no dependency). Its group and direct messages are never published.
- Web page: "Spawn" button and marker; check boxes to show or hide claims, players and chat (remembered per
  browser).
- In-game settings screen for operators (mod list "Config" or `/webmap config`): page title, favicon and the
  claims, players, online list and chat layers. Optional payloads; players without the mod still join.
- `web.favicon` files must be inside the server folder.

### [0.2.0] - 2026-11-02

- Optional **chat** (read only, with joins and leaves) and **list of connected players** on the page
  (`layers.showChat`, off by default; `layers.showOnlineList`, on by default). Clicking a player whose
  position is shown centres the map on them.
- Page **title** (`web.title`, default: the server MOTD), **favicon** (`web.favicon`: an http(s) URL or an
  image file relative to the server folder; default `server-icon.png` or the mod's icon) and **language**
  (`web.language`: AUTO, EN, ES). In Spanish the claims layer is called "Terrenos".
- The `http`, `web` and `layers` settings apply without restarting the game server: when the config file is
  saved, or with the new `/webmap reload`. A new port, bind address or thread count restarts only the web
  server; `/webmap restart` restarts it by hand. Open pages reload themselves.
- No more 404 errors in the browser console: tiles that do not exist yet answer a transparent pixel, and the
  page reloads only the tiles the server re-rendered (new `/api/changes`) instead of the whole map every two
  minutes, so new terrain shows up within seconds.

### [0.1.0] - 2026-11-01

- Web map served by the game server (default port 8123), with zoom-out levels and a dimension selector
  (Leaflet 1.9.4 bundled).
- Default style `DIORAMA`: the map is drawn at an angle like the Diorama map, from block textures, with
  sun, shadows, sky occlusion and outlines. Vanilla textures come from the official client jar, downloaded
  once from Mojang and checked against its SHA-1 (`render.downloadClientAssets`); modded blocks use the
  textures in their own jars. `render.style = TOP_DOWN` gives a flat map with vanilla map colours instead.
- Background rendering at minimum priority with a chunks-per-second cap; only changed areas are redrawn.
- Claims painted on the terrain they cover (colour per owner, edge where the owner changes, name and owner
  label) when Claims is installed; the mouse shows x, y, z and the claim. Optional player positions (off by
  default).
- `/webmap status|render|clearcache`; JSON endpoints `/api/info`, `/api/claims`, `/api/players`.

## clientmap

### [Unreleased]

### [0.3.0] - 2026-11-03

- **Share your own waypoints from Diorama's waypoint screen**: a "Share: Private / Public / Group" button under
  "Show on maps". Renaming, moving, recolouring or deleting the waypoint in Diorama updates (or withdraws) it for
  the others. Replaces "Add shared waypoint here" in the map menu and `/sharedwaypoint add|rename`;
  `/sharedwaypoint list|remove` stay.
- **Map settings** (map right-click menu "Map settings…", `/clientmap settings` or the mods list): show claims on
  the map, show claims on the minimap, share my location and share my explored map, all on by default.
  `/mapexplored share|keep` sets the last one without the client mod.
- What a player explores is always kept in their own pool too, so turning sharing on, or joining a group, hands
  over what they explored before. Several pools can be synced at once.
- Leaving or being removed from a map group withdraws the waypoints shared with that group.
- Map protocol 2 and storage format 2: server and clients must be updated together.

### [0.2.0] - 2026-11-02

- Support Diorama 0.7.0 (NeoForge 1.21.1), whose map right-click menu changed shape; Diorama 0.6.0 and
  0.6.1 stay supported.
- With Diorama 0.7.0's view of another dimension's map, "Add shared waypoint" adds it in the dimension
  shown; claiming and releasing are offered only in the player's own dimension.

### [0.1.0] - 2026-11-01

- Integration with Diorama 0.6.0 (NeoForge 1.21.1): claims on the map, claim/release from the right-click
  menu, shared explored map, shared waypoints and player positions with per-player privacy.
- Sharing modes per server: disabled, global or by groups (`/mapgroup`).
- Commands: `/sharedwaypoint`, `/maplocation`, client `/clientmap status|resync`.
