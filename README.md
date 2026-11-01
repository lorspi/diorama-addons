# Diorama Addons

A set of **independent** NeoForge mods for Minecraft 1.21.1 that integrate with
each other when installed together. Every mod builds its own jar and works on
its own.

| Mod | Mod ID | Side | Status |
|---|---|---|---|
| Claims API | `dioaddons_claims_api` | Library (bundled inside the other jars) | ✅ Read-only API |
| Claims | `dioaddons_claims` | Server (required) + client (optional GUI) | ✅ Phase 1: claims, friends, permissions, GUI, commands, protection |
| Web Map | `dioaddons_webmap` | Server only | 🚧 Phase 0 (empty skeleton) |
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

## Testing

```sh
./gradlew build                          # unit tests
./gradlew :claims:runGameTestServer      # in-world tests (protection, persistence, API)
```

## Documentation

- [Architecture and design decisions](docs/ARCHITECTURE.md)
- [Claims permissions and commands](docs/claims-permissions.md)

## License

[MIT](LICENSE). See [THIRD_PARTY.md](THIRD_PARTY.md) for third-party notices.
