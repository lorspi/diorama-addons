# Changelog

Each mod is versioned on its own with [Semantic Versioning](https://semver.org/). Releases are tagged
`<module>-v<version>`, for example `claims-v0.1.0`. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## claims-api

### [Unreleased]

### [0.1.0] - 2026-11-01

- First release: read-only API to query claims (`ClaimsApi`, `ClaimInfo`, `ChunkCoord`), change listener and
  `ClaimsApiProvider`. Bundled (Jar-in-Jar) by the other mods.

## claims

### [Unreleased]

### [0.1.0] - 2026-11-01

- Chunk-based claims with per-player limits (chunks and claims), contiguity and optional spawn distance.
- Friends with per-member permissions and templates (Visitor, Member, Trusted); public permissions and
  claim settings (PvP, explosions, fire spread, mob griefing).
- Claims screen: chunk grid with drag selection, claim list, members and public tabs (key **G** or
  `/claims menu`).
- Commands for players without the client mod: `/claim`, `/unclaim`, `/claims ...`, `/claims admin ...`.
- Protection for blocks, containers, items, entities, animals, farmland, explosions, fire, fluids, pistons
  and mob griefing.
- Messages translated on the server (English, Spanish), so vanilla clients read them too.
- Versioned storage; game tests for protection and persistence.

## webmap

### [Unreleased]

### [0.1.0] - 2026-11-01

- Web map served by the game server (default port 8123): top-down tiles with relief shading, zoom-out
  levels, dimension selector (Leaflet 1.9.4 bundled).
- Background rendering at minimum priority with a chunks-per-second cap; only changed chunks are redrawn.
- Claims layer (name and owner, colour per owner) when Claims is installed; optional player positions
  (off by default).
- `/webmap status|render|clearcache`; JSON endpoints `/api/info`, `/api/claims`, `/api/players`.

## clientmap

### [Unreleased]

### [0.1.0] - 2026-11-01

- Integration with Diorama 0.6.0 (NeoForge 1.21.1): claims on the map, claim/release from the right-click
  menu, shared explored map, shared waypoints and player positions with per-player privacy.
- Sharing modes per server: disabled, global or by groups (`/mapgroup`).
- Commands: `/sharedwaypoint`, `/maplocation`, client `/clientmap status|resync`.
