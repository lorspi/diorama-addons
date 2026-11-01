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
5. **`claims-api` will be bundled (jar-in-jar) by every mod that uses it**
   (planned for Phase 1). This way `webmap` can load the API classes even when
   `claims` is not installed; NeoForge's JarJar picks a single copy when several
   mods ship it. Integration code that touches the API at runtime still lives in
   separate classes guarded by `ModList.get().isLoaded(...)`.
6. **Development server EULA.** The `runServer` task writes `eula=true` into
   `<module>/run/server/` (development only, git-ignored).
7. **`webmap` is not marked as dedicated-server only.** It also runs in the
   integrated server (singleplayer/LAN) so it can be tested from `runClient`.
   It simply does nothing on the client side.

## Assumptions to review

- The GitHub organisation/repository URL is not set yet, so `issueTrackerURL`
  is omitted from `neoforge.mods.toml`.
