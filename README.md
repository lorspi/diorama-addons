# Diorama Addons

A set of **independent** NeoForge mods for Minecraft 1.21.1 that integrate with
each other when installed together. Every mod builds its own jar and works on
its own.

| Mod | Mod ID | Side | Status |
|---|---|---|---|
| Claims API | `dioaddons_claims_api` | Library (bundled inside the other jars) | ✅ Read-only API |
| Claims | `dioaddons_claims` | Server (required) + client (optional GUI) | ✅ Phase 1: claims, friends, permissions, GUI, commands, protection |
| Web Map | `dioaddons_webmap` | Server only | ✅ Phase 2: tiles, claims layer, optional players |
| Client Map | `dioaddons_clientmap` | Server + client, integrates with [Diorama](https://github.com/SunkenInTime/diorama) 0.6.0 | ✅ Phase 3: claims, shared explored map, shared waypoints, player positions |

## Requirements

- Minecraft **1.21.1**
- NeoForge **21.1.265** or newer

## Building

Requires a JDK to run Gradle; Java 21 is provisioned automatically through
Gradle toolchains if it is not installed.

```sh
./gradlew build                 # builds every mod and runs the tests
./gradlew :claims:runServer     # starts a development server with one mod
./gradlew :claims:runClient     # starts a development client
```

Jars are written to `<module>/build/libs/`.

## Claims

Protect your land chunk by chunk, add friends and decide exactly what each of them can do.

- Open the screen with **G** (configurable in Controls) or `/claims menu`.
- Left click or drag on the grid to claim free chunks or release your own; right click a claim to select it.
- Side panel: your claims and limits, rename/delete, **Members** tab (add friends, templates *Visitor*,
  *Member*, *Trusted*, per-permission toggles) and **Public** tab (what everyone else can do, plus PvP,
  explosions, fire spread and mob griefing).
- Players without the mod can join and use the commands: `/claim`, `/unclaim`, `/claims help`.

See [docs/claims-permissions.md](docs/claims-permissions.md) for every permission and command.

## Web Map

A live map of the world in the browser, served by the game server itself. Players install nothing.

- Open `http://<server>:8123/` (port and bind address in `config/dioaddons_webmap-common.toml`).
- Tiles are rendered in the background from the saved world, at low priority and with a chunks-per-second
  cap; only changed chunks are redrawn.
- With Claims installed, claims are drawn with a colour per owner and a label (name and owner). Members and
  permissions are never published.
- Player positions are **off by default** (`layers.showPlayers`).
- `/webmap status`, `/webmap render [dimension]`, `/webmap clearcache` for operators.

See [docs/webmap-api.md](docs/webmap-api.md).

## Client Map (Diorama integration)

Adds server-shared data to the [Diorama](https://modrinth.com/mod/diorama-map) map. Install it on the
server and on clients that use Diorama **0.6.0** (`0.6.0+neoforge-1.21.1`); other Diorama versions are
detected and left alone.

- **Claims** on Diorama's map and minimap, coloured by owner, with "claim · owner" on hover (needs Claims
  on the server). With Claims also on the client, right-click the map to **claim or release a chunk**.
- **Shared explored map:** chunks explored by others are drawn on your map, sent in the background.
- **Shared waypoints:** right-click → *Add shared waypoint here*; delete from the pin's menu; or
  `/sharedwaypoint add|list|rename|remove`.
- **Player positions** of your pool on the map and radar; hide yours with `/maplocation hide`.
- Server config (`config/dioaddons_clientmap-common.toml`): explored map and locations each `DISABLED`,
  `GLOBAL` or `GROUPS`. Groups: `/mapgroup create|invite|join|leave|kick|info`.
- Client commands: `/clientmap status`, `/clientmap resync`.

## Testing

```sh
./gradlew build                          # unit tests
./gradlew :claims:runGameTestServer      # in-world tests (protection, persistence, API)
```

## Documentation

- [Architecture and design decisions](docs/ARCHITECTURE.md)
- [Claims permissions and commands](docs/claims-permissions.md)
- [Web map HTTP API](docs/webmap-api.md)
- [Diorama research and integration plan](docs/clientmap-research.md)

## License

[MIT](LICENSE). See [THIRD_PARTY.md](THIRD_PARTY.md) for third-party notices.
