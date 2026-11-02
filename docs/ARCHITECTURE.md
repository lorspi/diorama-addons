# Architecture

## Repository layout

```
/
├─ settings.gradle.kts        includes the four modules, applies the foojay toolchain resolver
├─ gradle.properties          shared versions (Minecraft, NeoForge, Parchment, Java, mod id prefix)
├─ build-logic/               included build with the convention plugin `dioaddons.neoforge-mod`
├─ claims-api/                small API to query claims (no game logic)
├─ claims/                    land claims mod
├─ webmap/                    server-side web map
└─ clientmap/                 Diorama integration (claims, shared explored map, shared waypoints)
```

Each module has its own `gradle.properties` with `mod_suffix`, `mod_name`,
`mod_version` and `mod_description`. The convention plugin derives the mod id
(`<mod_id_prefix>_<mod_suffix>`), configures ModDevGradle, the `client` and
`server` runs, JUnit 5 and the jar name
(`diorama-addons-<module>-<mcversion>-<version>.jar`). `neoforge.mods.toml` is a
template expanded by `processResources`.

## Versions

| Item | Version | Notes |
|---|---|---|
| Minecraft | 1.21.1 | |
| NeoForge (compile/run) | 21.1.265 | |
| NeoForge (accepted at runtime) | `[21.1.265,)` | |
| ModDevGradle | 2.0.148 | latest stable on maven.neoforged.net on 2026-11-01 |
| Gradle | 9.8.2 | latest release on 2026-11-01 |
| Parchment | 1.21.1 / 2024.11.17 | parameter names in decompiled sources |
| Java | 21 | provisioned by Gradle toolchains (foojay) |

## Decisions

1. **Mod id prefix `dioaddons_`.** Project name "Diorama Addons". Ids:
   `dioaddons_claims_api`, `dioaddons_claims`, `dioaddons_webmap`,
   `dioaddons_clientmap`. Short, readable, and does not clash with Diorama's own
   id.
2. **Java package `io.github.dioramaaddons.<module>`.** Neutral package that does
   not depend on a domain we do not own. Easy to change before the first release.
3. **Shared build logic as an included build** (`build-logic`) with a precompiled
   script plugin, rather than `allprojects {}` blocks. Keeps each module's
   `build.gradle.kts` to a single `plugins {}` line.
4. **Optional dependencies only.** In `neoforge.mods.toml`, `webmap` and
   `clientmap` declare `dioaddons_claims` as `type = "optional"`. No mod
   requires another.
5. **`claims-api` is bundled (Jar-in-Jar) by every mod that uses it.** `claims`
   ships it today; `webmap` and `clientmap` will too. NeoForge's JarJar keeps a
   single copy (highest version). The API is plain Java (no Minecraft types:
   dimensions are ids like `minecraft:overworld`, chunks are packed longs), and
   the implementation is published through `ClaimsApiProvider` when a server
   with `claims` starts. In dev runs the API is loaded as its own mod from its
   source set.
6. **Development server EULA.** The `runServer` task writes `eula=true` into
   `<module>/run/server/` (development only, git-ignored).
7. **`webmap` is not marked as dedicated-server only.** It also runs in the
   integrated server (singleplayer/LAN) so it can be tested from `runClient`.
   It simply does nothing on the client side.

## Phase 1 — claims

### Layers

| Package | Depends on Minecraft | Content |
|---|---|---|
| `claims.core` | no | `Permission`, `PermissionTemplate`, `Claim`, `ClaimStore` (every rule), `PermissionResolver` |
| `claims.data` | DFU only (codecs), `SavedData` | Versioned codecs, `ClaimSavedData` |
| `claims.server` | yes | `ClaimsService` (state per running server, API implementation, GUI sync), `ServerLang` |
| `claims.protection` | yes | Event handlers and hooks called by mixins |
| `claims.network` | yes | Optional payloads and server-side validation |
| `claims.command` | yes | Brigadier commands |
| `claims.client` | yes (client only) | Key binding, screen |

### Decisions

8. **Permission set.** The suggested list was kept. Permissions are split into
   *player* permissions (given to members/public) and *claim* settings (`pvp`,
   `explosions`, `fire_spread`, `mob_griefing`), which are stored in the public
   set and apply to everyone. See `claims-permissions.md`.
9. **Members get the union of their permissions and the public ones.** The
   resolution order owner → member → public is kept, but a friend never has
   fewer rights than a stranger.
10. **Limits are per player, global for the server** (config), as confirmed.
    Being a member of other claims does not count.
11. **Contiguity.** With `requireContiguous`, new chunks join an adjacent claim of
    the same owner, and releasing a chunk that would split a claim is refused.
    Batch operations (GUI drag) retry chunks until no more progress is possible,
    so the selection order does not matter.
12. **Storage.** One `SavedData` file in the overworld data folder
    (`data/dioaddons_claims.dat`) with `format_version`. Permissions are stored
    by name, unknown names are ignored; a newer format refuses to load instead of
    overwriting data.
13. **Fire and fluids use mixins** because NeoForge 21.1 has no event for them:
    `FireBlock.checkBurnOut` / `FireBlock.getIgniteOdds(LevelReader, BlockPos)`
    and `FlowingFluid.canSpreadTo`. Everything else uses NeoForge events
    (`BlockEvent.BreakEvent`, `EntityPlaceEvent`, `PlayerInteractEvent.*`,
    `AttackEntityEvent`, `LivingIncomingDamageEvent`, `FarmlandTrampleEvent`,
    `BlockToolModificationEvent`, `ExplosionEvent.Detonate`, `PistonEvent.Pre`,
    `FluidPlaceBlockEvent`, `EntityMobGriefingEvent`, `LivingDestroyBlockEvent`).
14. **Server-side translations.** Messages are `translatableWithFallback`
    components whose fallback is translated on the server with the player's
    client language (bundled `en_us`/`es_es`). Clients with the mod use their own
    language files; vanilla clients still read a proper message.
15. **Networking.** All payloads are registered with `.optional()`. The client
    sends requests (`request_state`, `claim_chunks`, `edit_claim`,
    `unsubscribe`); the server validates ownership, limits, names, member counts
    and that chunks are within `guiMaxRadius` of the player, then answers with a
    full `state` payload. Players with the screen open are "subscribed" and get a
    fresh state when claims change or when they move to another chunk.
16. **GUI map has no terrain.** The grid shows ownership colours only; drawing
    terrain is left for `clientmap`/Diorama.
17. **GameTests ship in the main source set** (`claims.gametest`). NeoForge only
    registers game tests in development, so they are inert in production. Run them
    with `./gradlew :claims:runGameTestServer`.

## Phase 2 — webmap

| Package | Depends on Minecraft | Content |
|---|---|---|
| `webmap.render` (pure part) | no | `PackedArray`, `ChunkData`, `ChunkRenderer`, `Shading`, `TileMath`, `Downsampler` |
| `webmap.render` | yes | `ChunkNbt` (NBT → `ChunkData`), `McBlockColors`, `TileStore` (disk), `TileRenderer` (thread) |
| `webmap.layer` | claims-api only / yes | `ClaimGeometry`, `OwnerColors` (pure), `ClaimsLayer`, `PlayersLayer` |
| `webmap.http` | no (JDK) | `WebServer` |

### Decisions

18. **HTTP server: the JDK's `com.sun.net.httpserver`** (module `jdk.httpserver`), no extra dependency.
    Port 8123 and bind address are in a COMMON config (`config/dioaddons_webmap-common.toml`, one file
    for the whole server, not per world). A busy port logs an error and the server keeps running.
19. **Reading chunks through `ChunkMap.read`** instead of parsing `.mca` files by hand. It runs on the
    storage IO worker, sees chunks that are not yet flushed, and never blocks the server thread. The
    render thread has minimum priority and a configurable chunks-per-second cap.
20. **Tile scheme = region files.** Zoom 0 tiles are 512×512 blocks (one region), so a full render maps
    1:1 to the region files on disk; zoomed-out levels are built from their four children.
21. **Colours from vanilla `MapColor`** (`Block.defaultMapColor()`), without biome tinting. This keeps
    the renderer independent from client resources and matches the in-game map style.
22. **Claims layer through claims-api only.** The API is bundled (Jar-in-Jar) in the webmap jar, so the
    classes always exist; without the claims mod no implementation is registered and the layer is
    reported as unavailable. The API does not expose members or permissions at all, so the web cannot
    leak them. Outlines are computed on the server.
23. **Player positions off by default** (`layers.showPlayers`); spectators are always hidden.
24. **`-Pdioaddons.withClaims=true`** adds the claims mod to the webmap dev runs, to test both together
    without making webmap depend on claims.
25. **Server-side messages** use the same translatable-with-fallback approach as claims (small copy of
    the helper, since mods do not depend on each other).

### Known limitations

- Restarting the server re-queues regions whose `.mca` file was written after the tile (usually the ones
  saved at shutdown). It is throttled like any other render.
- Chunks are re-rendered when unloaded even if nothing changed (the plan asks for unload events); the
  chunks-per-second cap keeps the cost bounded.
- No isometric view yet; no biome tinting.

## Phase 3 — clientmap (Diorama integration)

Research and the list of Diorama entry points: [clientmap-research.md](clientmap-research.md).

| Package | Side | Content |
|---|---|---|
| `clientmap.core` | pure | `ShareMode`, `Scopes`, `ExploredLog`, `GroupRegistry`, `SharedWaypoint` |
| `clientmap.server` | server | `ClientMapServer` (streams, players, waypoints, claims feed), `ClientMapData`, commands |
| `clientmap.network` | both | optional payloads |
| `clientmap.client` | client | handshake, chunk rebuilding, sync progress; never touches Diorama classes |
| `clientmap.compat.diorama` | client | `DioramaCompat` (version gate), `DioramaBridge`, `DioramaMenus`, mixins + mixin plugin |
| `clientmap.compat.claims` | client | sends the claims mod's own `claim_chunks` payload; loaded only when that mod is present |

### Decisions

26. **clientmap has a server part.** Claims, the explored map, shared waypoints and player positions all
    come from the server, so the same jar runs on both sides. Without Diorama on the client nothing is
    requested and nothing is sent.
27. **Opt-in handshake.** Only clients with a supported Diorama send `hello`; the server sends map data to
    those clients only. All payloads are optional, so vanilla clients and clients without clientmap join.
28. **Pools (`Scopes`).** `exploredMap.mode` and `locations.mode` are `DISABLED`, `GLOBAL` or `GROUPS`
    (config, as decided). In `GROUPS` mode a player without a group keeps a pool of their own; joining a
    group merges their explored chunks and waypoints into it.
29. **Groups are clientmap's own** (`/mapgroup create|invite|join|leave|kick|info`), because claims
    members are per claim, not symmetric groups, and clientmap must work without claims.
30. **Explored chunks** are recorded from `ChunkWatchEvent.Watch` (a chunk sent to a player) into an
    append-only log per pool and dimension; the position in the log is the sequence number clients resume
    from. Chunks are sent oldest first, at `chunksPerSecond` per player, read from the live chunk when
    loaded and from storage (`ChunkMap.read`) otherwise. Only block and biome sections are sent.
31. **Claims to Diorama** go through claims-api on the server (no claims mod needed on the client to
    see them); claiming from the map uses the claims mod's own payload, so it needs that mod on the client
    and is validated by the same server code as the GUI. The claims server now answers requests from
    players without the screen open on the action bar, and reports "too far" explicitly.
32. **Shared waypoint permissions:** the owner, the owner of the group whose pool it is in, and operators
    may rename or delete it. Limits per pool are configurable.
33. **Privacy:** each player can hide their position (`/maplocation hide`); default from config.
    Spectators are never sent. Positions go only to players in the same pool.
34. **Diorama version gate.** A mixin plugin applies the Diorama mixins only for versions listed in
    `DioramaCompat.SUPPORTED` (now `0.6.0`, `0.6.1` and `0.7.0`, all `+neoforge-1.21.1`); the dependency is
    optional with any version so other versions do not stop the game from loading. Each supported version
    maps to the shape of the internals we hook (`DioramaCompat.Internals`); a mixin whose target only
    exists in some versions is applied only to those (`MapScreenSpotV06Mixin` for
    `spotItems(Spot)` in 0.6.x, `MapScreenSpotV07Mixin` for `spotItems(Spot, BlockPos)` in 0.7.0), so
    injectors keep `defaultRequire 1` and a missing target still fails loudly. clientmap compiles against
    the newest supported version (0.7.0); older ones stay supported while it costs one small mixin.

## Section 10 — open source

35. **Versions and tags per mod.** Each module keeps `mod_version` in its own `gradle.properties`;
    releases are tagged `<module>-v<semver>` (`claims-v0.1.0`, `claims-api-v0.1.0`...). One
    `CHANGELOG.md` with a section per module.
36. **Release workflow** (`.github/workflows/release.yml`) runs on those tags:
    `.github/scripts/release_info.py` checks the tag format, that `mod_version` matches and that the
    changelog has an entry, then the mod is built and tested and `gh release create` publishes its jar
    with the changelog entry as notes. Only GitHub's own CLI is used, no third-party release action.
    Modrinth/CurseForge publishing is left for later.
37. **claims-api is released as its own tag** for API consumers, even though players never install it
    separately (it is bundled in the other jars).

## Terrain in the Diorama style

38. **`render-core` module** (`dioaddons_render_core`): pure-Java terrain shading shared by the mods that
    draw maps, bundled Jar-in-Jar like claims-api. `TopDownShader` adapts the Diorama map's light model
    (MIT, see THIRD_PARTY.md) to a view from straight above: warm sun and cool sky light in linear space,
    cast shadows traced over the height field, sky occlusion from the horizon in eight directions, crease
    occlusion, height shade, slope light (seen from above, slopes show no side faces), outlines at the foot
    of higher ground (strong at cliffs, faint at one-block steps), shallow water showing the floor, and the
    same colour grade and default values.
39. **Claims screen terrain is top-down**, not angled, so the chunk grid stays square and clicks map to one
    chunk. Colours are the average of each block's top texture taken from the game's own baked models
    (resource packs and modded blocks included) with biome tints, as Diorama does. Only chunks loaded on the
    client are drawn; the rest stays dark. Sampling runs on the client thread; shading runs on a background
    thread; the picture is refreshed when the grid moves and every 5 seconds.

40. **Web map in the Diorama style** (`render.style = DIORAMA`, default). `ObliqueRenderer` (render-core)
    traces one ray per pixel through the blocks, in the y-z plane of the pixel's column: camera looking
    north and down at 50°, map space `u = x`, `v = z·sin − y·cos`, tiles of 128 units at 4 px per unit like
    Diorama. The hit face (top or south) is lit with the Diorama light model, with shadows traced in 3D
    (40 blocks) and outlines where a neighbouring pixel is 1.5 blocks nearer. Light is cached per face.
41. **Hit maps.** Next to each tile a PNG stores, per pixel, the block it shows (`z` relative to the tile
    row's lowest visible z, `y` at half resolution). The page uses it to paint claims on the terrain they
    really cover, to place claim labels where they show, and to show x/y/z under the mouse. Claims are
    painted in the browser, so claim changes never re-render tiles.
42. **Block colours on the server.** A dedicated server has no vanilla textures, so the official client jar
    is downloaded once from Mojang (manifest → version → `downloads.client`, SHA-1 checked) into
    `<server>/dioaddons_webmap/` on the render thread, never blocking start-up. Mod jars are read for modded
    blocks. A small resolver follows block states → models → parents → face textures and tint indexes;
    grass and foliage tints use the biome climate and the vanilla colour maps. Without the jar, vanilla
    blocks fall back to map colours.
43. **Invalidation.** A saved or unloaded chunk marks the tiles of its column that can show it (and the
    next column east, where its shadows fall); the chunk is dropped from the renderer's cache before the
    next tile. Tiles with nothing in view leave an `.empty` marker so restarts skip them.

## Version 0.2 (claims, webmap)

44. **Admin mode in the claims screen reuses the bypass flag.** Operators toggle it with a new optional
    `admin_action` payload (also `TELEPORT` and `TRANSFER`); every admin action checks the operator level on
    the server again. In admin mode the state lists every claim (not only the ones on the grid), so remote
    claims can be managed; the screen filters the list client-side. Ownership transfer lives in `ClaimStore`
    (pure, tested) and ignores the new owner's limits. The claims network version went to `2` because
    `state` changed; NeoForge refuses to connect mismatched versions with a clear message.
45. **Web map settings that apply live.** `http`, `web` and `layers` are re-read on `ModConfigEvent.Reloading`
    and with `/webmap reload` (parses the TOML with night-config, corrects it against the spec and copies it
    into the loaded config), because NeoForge's file watcher gets no events on some file systems (verified
    on a Windows folder mounted in WSL). Only the web server restarts, and only when its own settings changed.
    The page polls `/api/info` and reloads when `revision` changes.
46. **Chat on the web** comes from `ServerChatEvent` at lowest priority, ignoring cancelled messages, plus
    login/logout events; kept in memory only and never sent when `layers.showChat` is off (default).
47. **No 404s for tiles.** Missing tiles answer a 1×1 transparent PNG. `TileStore` reports every write and
    delete to a bounded `TileChangeLog` (pure, tested); the page asks `/api/changes` every 5 s and reloads only
    those tiles in place (cache-busting query), instead of redrawing the whole map every two minutes.
48. **Favicon** is an http(s) URL (redirect) or an image file relative to the server folder; other file types
    are refused so the setting cannot be used to publish arbitrary files.
49. **Web page language** follows `web.language` (AUTO uses the browser's languages); the claims layer is
    "Terrenos" in Spanish.

## Version 0.3 (webmap)

50. **Live terrain.** The map used to see only saved chunks, so explored land showed up after the autosave
    (five minutes) or when unloaded. `LiveChunks` tracks loaded full chunks (`ChunkEvent.Load`; `isNewChunk`
    means generated or promoted to full) and, every `liveCheckSeconds`, fingerprints the top block of each
    column on the server thread within `liveBudgetMicros` per tick. New or changed chunks are copied (status,
    surface heightmap, block and biome palettes, the saved-chunk format the renderers already parse) into
    `LiveChunkStore`, which renderers read before disk. Snapshots are dropped on save and unload. Edits below
    the surface are only picked up on save, as before.
51. **Zoomed-out refresh** every 15 s during long level-0 queues (parents used to wait for an empty queue,
    which never happens while someone explores, so zoomed-out views stayed black).
52. **Mochos Esenciales claims** are read by reflection (`MochosClaimSource`): it ships a copy of claims-api in
    its own package, so neither mod depends on the other. Providers are re-checked every 5 s; when the layer
    appears or disappears `/api/info` gets a new revision and pages reload.
53. **In-game settings** use optional payloads (`config_request`, `config_update`, `config_state`); the server
    checks permission level 2 (or the single-player owner), cleans the strings, writes the COMMON config with
    `ModConfigSpec.save()` and applies it. The screen is registered as the mod's `IConfigScreenFactory`.
54. **Layer check boxes** on the page are per visitor (localStorage) and only offer layers the server
    publishes; turning a layer off on the server still removes it for everyone.

## Assumptions to review

- Repository: https://github.com/lorspi/diorama-addons (`issueTrackerURL` points to its issues).
