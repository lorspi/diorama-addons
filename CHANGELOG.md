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
