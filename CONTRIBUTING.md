# Contributing

Thanks for helping! Bug reports, ideas and pull requests are welcome. Please open an issue first for
large changes so we can agree on the approach.

## Project layout

One Gradle repository, four independent NeoForge mods (Minecraft 1.21.1, NeoForge 21.1.265+, Java 21):
`claims-api`, `claims`, `webmap`, `clientmap`. Shared build settings live in `build-logic`. Read
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) before changing how the mods talk to each other.

Ground rules:

1. **No mod requires another.** Dependencies between them are `optional` in `neoforge.mods.toml`. Code that
   uses another mod's classes goes in its own class, loaded only when that mod is present.
2. **Players without the mod can still join.** Network payloads are registered with `.optional()`, and
   everything has a command alternative.
3. **Never trust the client.** Every request is validated on the server.
4. **Do not block the server thread** with rendering, disk or network work.
5. **Game rules in plain classes** (no Minecraft types) with unit tests.
6. **Do not guess Minecraft or NeoForge APIs**: check the decompiled sources Gradle generates.
7. Settings go in `ModConfigSpec` configs with comments. Texts use translation keys with `en_us` and `es_es`.
8. Code, comments and commits in English.

## Building and testing

```sh
./gradlew build                              # every mod + unit tests
./gradlew :claims:runGameTestServer          # in-world tests for claims
./gradlew :claims:runServer                  # dev server with one mod (run/server, EULA accepted)
./gradlew :claims:runClient                  # dev client
./gradlew :webmap:runServer -Pdioaddons.withClaims=true          # add claims to another mod's runs
./gradlew :clientmap:runClient -Pdioaddons.withDiorama=true      # add Diorama (from Modrinth)
./gradlew :claims:runClient -Pdioaddons.join=127.0.0.1:25565 -Pdioaddons.username=Tester
```

Before a pull request: `./gradlew build` passes, the change was tried in game (say how in the PR), and a
vanilla client can still join a server with the changed mod.

## Commits

[Conventional Commits](https://www.conventionalcommits.org/) with the mod as scope, small and focused:
`feat(claims): ...`, `fix(webmap): ...`, `docs: ...`, `build: ...`.

## Changelog and versions

Each mod has its own [SemVer](https://semver.org/) version in `<module>/gradle.properties`
(`mod_version`). Add your change to `CHANGELOG.md` under `[Unreleased]` of every mod it touches.

## Releasing (maintainers)

1. Move the mod's `[Unreleased]` notes in `CHANGELOG.md` to a new `### [x.y.z] - YYYY-MM-DD` entry.
2. Set `mod_version=x.y.z` in `<module>/gradle.properties` and commit (`chore(<module>): release x.y.z`).
3. Tag and push: `git tag -a <module>-vx.y.z -m "<module> x.y.z" && git push origin <module>-vx.y.z`.

The **Release** workflow checks that the tag, `mod_version` and the changelog entry agree, builds and tests
that mod, and creates a GitHub Release with its jar and the changelog entry as notes. Publishing to
Modrinth or CurseForge is not automated yet.

## Diorama integration

`clientmap` relies on Diorama internals and only enables itself for versions listed in
`DioramaCompat.SUPPORTED`. To support a new Diorama version, re-check every entry point listed in
[docs/clientmap-research.md](docs/clientmap-research.md) against that jar (`javap`), test in game, then add
the version to the list.

## License

By contributing you agree that your contribution is licensed under the [MIT License](LICENSE). Code copied
from elsewhere must keep its license notice and be listed in [THIRD_PARTY.md](THIRD_PARTY.md).
