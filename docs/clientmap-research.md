# clientmap: Diorama research

Studied: Diorama (`https://github.com/SunkenInTime/diorama`, MIT, client only), source at tag `v0.6.0`
and the released jar `diorama-0.6.0+neoforge-1.21.1.jar` from Modrinth (SHA-512 checked). Every class
and method used below was checked with `javap` in that jar, because the repository is a Stonecutter tree
whose source differs per Minecraft version. Re-checked the same way for `0.6.1+neoforge-1.21.1` (GitHub
release asset) and `0.7.0+neoforge-1.21.1` (Modrinth); see [Diorama 0.7.0](#diorama-070).

## 1. Public API or extension points for layers, waypoints or map data?

**No.** Diorama has no API package, no registry, no events for other mods and no documented extension
points. Its only extension points are for resource packs (radar icons, waypoint icons, minimap frames).
Integrations with other mods (Xaero's channels, FTB Chunks, Waystones, Create copycats, Cobblemon,
Pixelmon) are written inside Diorama, by reflection against those mods.

What it does have are **public static entry points used by its own integrations**, which another mod
can call:

| Entry point (0.6.0 to 0.7.0) | Used by Diorama for |
|---|---|
| `dev.diorama.Claims.updated(ResourceKey<Level>, int chunkX, int chunkZ, UUID team, boolean forceLoaded)` | FTB Chunks claim updates |
| `dev.diorama.compat.FtbChunks.available()` / `team(UUID)` | Whether claims are shown, and a team's colour and name |
| `dev.diorama.MapSession.get().map()` → `MapLevel.put(ChunkSnapshot, int[], int, int)` | Every chunk the client receives |
| `dev.diorama.data.ChunkSnapshot.take(LevelChunk, ClientLevel)` | Snapshot of a chunk for baking |
| `dev.diorama.TrackedPlayers.update(CompoundTag)` (package-private) | Xaero player-tracker positions |
| `dev.diorama.waypoint.WaypointStore.in(String)` / `waystones()` | Waypoints shown, including Waystones' |
| `dev.diorama.MapMenu.Item.info/action/confirmed(...)` | Lines of the right-click menu |

These are internal: they change between releases (0.5.0 has no `Claims` class at all, and
`MapScreen.spotItems` takes a `BlockPos` there instead of a `MapLevel.Spot`; 0.7.0 takes both).

## 2. How is FTB Chunks integrated? Can it be replicated with claims-api?

- `FtbChunksUpdateMixin` (a `@Pseudo` mixin) runs after FTB Chunks' client task
  `UpdateChunkFromServerTask.runMapTask` and calls `FtbChunks.updated(task)`, which reads the task's fields
  by reflection and calls `Claims.updated(dimension, x, z, teamId, forceLoaded)`.
- `Claims` keeps chunk → team per dimension. To know a team's colour and name it calls
  `FtbChunks.team(UUID)`, which asks FTB Teams by reflection. Every tick `Claims.tick` builds a snapshot for
  the maps — **only if `FtbChunks.available()` is true**.
- The map paints each chunk in its team colour, draws a stronger edge where the owner changes, and shows
  the team name on hover.

**Replicable: yes**, without FTB Chunks:
1. The clientmap server reads claims through claims-api and sends them to the client.
2. The client calls `Claims.updated(...)` with the claim id as the "team".
3. A mixin makes `FtbChunks.available()` return true while our data is active (only when FTB Chunks is not
   installed) and makes `FtbChunks.team(id)` answer with our claim: colour from the owner, name
   "claim · owner".

## 3. How to add "Claim / release chunk" to the right-click menu?

`MapScreen.openMenu` builds a `MapMenu` from the private method
`List<MapMenu.Item> spotItems(MapLevel.Spot)` (and `waypointItems(Waypoint)` for a pin). There is no hook,
so a mixin injects at `RETURN` of `spotItems`, copies the list and adds items built with the public
`MapMenu.Item.action(...)`/`confirmed(...)` factories. The spot's `BlockPos` gives the chunk; the private
`dimension()` (shadowed) gives the dimension shown. In 0.7.0 the method is
`spotItems(MapLevel.Spot, BlockPos)` and `dimension()` is public, so there is one mixin per shape.

The action sends the **claims mod's own `claim_chunks` payload**, so the server validates it exactly like
the GUI. That code lives in a class loaded only when the claims mod is installed on the client.

## 4. How are explored chunks and waypoints stored, and how can the server feed them?

- **Explored map:** Diorama has no list of explored chunks. What it remembers is the **baked image**:
  `data/TileStore` writes one file per tile (`OMT1` format, deflated pixels plus spots/chunks) under
  `.minecraft/diorama/<world>/<dimension>/<map>/`, with overview levels `lod<n>`. Tiles are baked from
  `ChunkSnapshot`s of the chunks the client received.
  `MapLevel.put(snapshot, null, ...)` marks the tiles under the chunk as unsaved; unsaved tiles are baked
  and written in the background **even when they are not on screen**, and chunks under them are kept
  until then. **Feeding:** the server sends the block and biome sections of chunks explored by others;
  the client builds a `LevelChunk` from them and calls
  `MapSession.get().map().put(ChunkSnapshot.take(chunk, level), null, px, pz)`. Diorama then draws and
  saves them like chunks it saw itself. Light is not sent: Diorama treats sections without sky light as
  fully lit, which is right for the surface view.
- **Waypoints:** `WaypointStore` keeps them as JSON per world. `Waystones` shows external waypoints
  without saving them: `WaypointStore.in(dimension)` and `waystones()` append them, with kind
  `WAYSTONE`, which Diorama treats as read-only. **Feeding:** a mixin appends the shared waypoints the
  same way. Edits and deletion go through our own menu items and commands, validated by the server.
- **Player positions:** Diorama shows far-away players from Xaero's tracker channel through
  `TrackedPlayers.update(CompoundTag)` (`i` UUID ints, `x`, `y`, `z`, `d` dimension, `r` removed). The radar
  draws them with their skin. **Feeding:** an `@Invoker` mixin calls it with positions from our own payload.
  The server decides who is visible (privacy and scope).

## 5. Which Diorama version works with NeoForge 21.1.x?

Diorama publishes `+neoforge-1.21.1` builds from 0.2.0 on. All declare NeoForge `[21.1.178,)` and
Minecraft `[1.21.1]`, so they run on 21.1.265. `Claims`/`FtbChunks` exist from **0.6.0**. 0.6.0 and 0.6.1
have `spotItems(MapLevel.Spot)`; 0.7.0 has `spotItems(MapLevel.Spot, BlockPos)`. (An earlier note said
0.6.1 changed `spotItems`: that was unreleased master; the released 0.6.1 jar has the 0.6.0 signature.)

## Integration plan

| Capability | Diorama point used | Kind |
|---|---|---|
| Show claims (owner colour, hover "claim · owner") | `Claims.updated` + `FtbChunks.available/team` | public call + mixin |
| Claim / release from the map | `MapScreen.spotItems` → `MapMenu.Item.action` | mixin (`RETURN`), one per signature |
| Shared explored map | `MapLevel.put(ChunkSnapshot.take(...))` | public calls |
| Shared waypoints | `WaypointStore.in` / `waystones` (as `WAYSTONE` kind); delete via `MapScreen.waypointItems` | mixins |
| Player positions | `TrackedPlayers.update` | `@Invoker` mixin |

**Supported versions:** exactly `0.6.0+neoforge-1.21.1`, `0.6.1+neoforge-1.21.1` and
`0.7.0+neoforge-1.21.1` (compiled against 0.7.0). The mixins live in `clientmap/compat/diorama`, are listed
in their own mixin config and are switched on by a mixin plugin only when one of those exact versions is
loaded; the plugin also picks the `spotItems` mixin that fits it (`MapScreenSpotV06Mixin` for 0.6.x,
`MapScreenSpotV07Mixin` for 0.7.0), so every injector that is applied has its target (`defaultRequire 1`). With another Diorama version, or without Diorama, clientmap loads,
logs one line and does nothing on the client. The server part of clientmap (storage, sync, commands)
works regardless.

## Diorama 0.7.0

Compared with `javap -p -c` against 0.6.0, for every class, method and field the compat layer uses:

- **Changed:** `MapScreen.spotItems(MapLevel.Spot)` became `spotItems(MapLevel.Spot, BlockPos)`. The new
  argument is a rough position under the cursor, used by Diorama only when the spot is not mapped (it
  offers "copy coordinates" there). `MapScreen.dimension()` became public and now returns the dimension
  *shown*, which can be another one than the player's since 0.7.0 can browse the saved map of other
  dimensions. Our menu only offers claim/release when the shown dimension is the player's, as before, and
  adds shared waypoints in the shown dimension.
- **Unchanged (signature and bytecode):** `Claims`, `Claims.Claim`, `compat.FtbChunks`, `TrackedPlayers`,
  `waypoint.WaypointStore`, `waypoint.Waypoint` (fields `id`, `kind`, `visible`, `dimension`, `PALETTE`),
  `MapLevel.Spot`, `MapScreen.waypointItems(Waypoint)`. Diorama still calls `FtbChunks.available/team` only
  from `Claims`, `TrackedPlayers.update` from `XaeroChannels`, and `WaypointStore.in/waystones` from the
  same places.
- **Compatible changes:** `MapMenu.Item` gained a `stays(...)` factory and a private field; `info`,
  `action` and `confirmed` are the same. `MapSession` gained `savedChunks()`/`lights()`; `get()` and
  `map()` are the same. `MapLevel.put(ChunkSnapshot, int[], int, int)` and `chunk(int, int)` keep their
  signatures; `put` now also records live chunks for the new "fill in from world save" import, which does
  not change how our fed chunks are baked. `ChunkSnapshot.take(LevelChunk, ClientLevel)` is unchanged
  (a `fromSave` factory was added).
- **No new extension point.** The 0.7.0 release notes and the jar add no API package, registry or event;
  the new classes (`SavedChunkImport`, `Lights`, `MinimapFrames`, `compat.Pixelmon`...) are internal. The
  mixins stay.

## Known limitations

- **Tied to Diorama internals.** Any Diorama update may move these methods; each new version has to be
  checked and added to the supported list.
- **Shared waypoints appear in Diorama's "Waystones" folder.** It is the only read-only kind Diorama has;
  a folder of our own would need much deeper mixins.
- **Shared waypoints are shared from Diorama's waypoint screen** (a button added by a mixin) and stay in step
  with the local waypoint; other players' shared waypoints are read-only for them (an owner, group owner or
  operator can delete them from the pin's menu or with `/sharedwaypoint remove`).
- **Explored chunks are sent as block data**, so the first sync on a big server takes time and bandwidth
  (rate-limited, oldest first). The client remembers how far it got per server, dimension and scope.
  Terrain that changes after exploration is updated when someone with the client mod sees it again.
- **No caves or light for received chunks:** only the surface view is filled in.
- Claims and player positions on the map follow the server's privacy settings; the Xaero "fair play" code
  still turns Diorama's radar off.
