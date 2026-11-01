# Diorama Addons

A set of **independent** NeoForge mods for Minecraft 1.21.1 that integrate with
each other when installed together. Every mod builds its own jar and works on
its own.

| Mod | Mod ID | Side | Status |
|---|---|---|---|
| Claims API | `dioaddons_claims_api` | Library (bundled inside the other jars) | ✅ Read-only API |
| Claims | `dioaddons_claims` | Server (required) + client (optional GUI) | ✅ Phase 1: claims, friends, permissions, GUI, commands, protection |
| Web Map | `dioaddons_webmap` | Server only | ✅ Phase 2: tiles, claims layer, optional players |
| Client Map | `dioaddons_clientmap` | Server + client, integrates with [Diorama](https://github.com/SunkenInTime/diorama) | 🚧 Phase 0 (empty skeleton) |

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

## Testing

```sh
./gradlew build                          # unit tests
./gradlew :claims:runGameTestServer      # in-world tests (protection, persistence, API)
```

## Documentation

- [Architecture and design decisions](docs/ARCHITECTURE.md)
- [Claims permissions and commands](docs/claims-permissions.md)
- [Web map HTTP API](docs/webmap-api.md)

## License

[MIT](LICENSE). See [THIRD_PARTY.md](THIRD_PARTY.md) for third-party notices.
