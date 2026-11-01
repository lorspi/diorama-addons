# Diorama Addons

A set of **independent** NeoForge mods for Minecraft 1.21.1 that integrate with
each other when installed together. Every mod builds its own jar and works on
its own.

| Mod | Mod ID | Side | Status |
|---|---|---|---|
| Claims API | `dioaddons_claims_api` | Library | 🚧 Phase 0 (empty skeleton) |
| Claims | `dioaddons_claims` | Server (required) + client (optional GUI) | 🚧 Phase 0 (empty skeleton) |
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

## Documentation

- [Architecture and design decisions](docs/ARCHITECTURE.md)

## License

[MIT](LICENSE). See [THIRD_PARTY.md](THIRD_PARTY.md) for third-party notices.
