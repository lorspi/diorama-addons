# Changelog — Diorama Addons: Web Map

All notable changes to `webmap`. Releases are tagged `webmap-v<version>`. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.3.0] - 2026-11-02

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

## [0.2.0] - 2026-11-02

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

## [0.1.0] - 2026-11-01

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
