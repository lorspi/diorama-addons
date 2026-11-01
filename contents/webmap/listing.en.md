# Diorama Addons: Web Map — listing for Modrinth and CurseForge

Everything needed to create the project page and upload version 0.1.0. Copy each block into the matching
field.

---

## 1. Project basics (both platforms)

| Field | Value |
|---|---|
| Name | `Diorama Addons: Web Map` |
| Slug / URL | `diorama-addons-webmap` |
| Icon | `contents/icons/webmap-icon.png` (512×512 PNG, under 256 KiB) |
| License | MIT |
| Source code | `https://github.com/lorspi/diorama-addons` |
| Issue tracker | `https://github.com/lorspi/diorama-addons/issues` |
| Wiki / docs | `https://github.com/lorspi/diorama-addons/blob/main/docs/webmap-api.md` |

### Summary (Modrinth "Summary", max 256 chars · CurseForge "Summary")

```
A live map of your server in the browser, drawn in the Diorama style with real textures, sun and shadows. Server-side only: players install nothing. Shows land claims when Claims is installed.
```

## 2. Modrinth fields

| Field | Value |
|---|---|
| Project type | Mod |
| Categories (primary) | `Utility`, `Management` |
| Additional categories | `Social` |
| Client side | Unsupported |
| Server side | Required |

## 3. CurseForge fields

| Field | Value |
|---|---|
| Project type | Mods |
| Main category | Map and Information |
| Additional categories | Server Utility |

---

## 4. Description (Markdown — paste it in both platforms)

> On CurseForge switch the editor to Markdown before pasting. The images point to the GitHub repository; if
> it is private, upload them to the gallery first and replace the URLs.

```markdown
# Diorama Addons: Web Map

**Your whole world in the browser — drawn at an angle in the style of the Diorama map, with real block textures, sun and shadows.**

![Web map with claims](https://raw.githubusercontent.com/lorspi/diorama-addons/main/docs/images/webmap-claims.png)

Web Map runs inside your NeoForge 1.21.1 server and serves a live map at `http://<your-server>:8123/`. Players don't install anything: they just open a link.

## Features

- 🏰 **Diorama-style rendering** — the world is drawn at an angle from real block textures, with sun, shadows and outlines. Trees and cliffs stand up off the map.
- 🧩 **Modded blocks look right** — they use the textures from their own jars.
- 🐢 **Light on your server** — tiles are rendered in the background, at low priority and with a chunks-per-second cap. Only changed areas are redrawn.
- 🔍 **Zoom and dimensions** — zoom-out levels and a selector for every dimension you enable in the config.
- 🚩 **Land claims** — with [Diorama Addons: Claims](https://modrinth.com/mod/diorama-addons-claims) installed, claims are painted on the terrain with a colour per owner and a label with the claim name and owner. Members and permissions are never published.
- 🔒 **Private by default** — player positions are **off** unless you turn them on, because the map may be open to the internet.
- 🛟 **Never takes the server down** — if the port is taken, it logs a clear error and the game keeps running.
- 🗂️ **JSON API** — `/api/info`, `/api/claims` and `/api/players` for your own tools.
- 📄 **Flat map option** — set `render.style = "TOP_DOWN"` for a classic top-down map.

## Installation

| Where | Needed? |
|---|---|
| Server | **Required** |
| Client | Not needed |

Requires **Minecraft 1.21.1** and **NeoForge 21.1.265 or newer**.

1. Drop the jar in the server's `mods` folder and start the server.
2. Open `http://<server-address>:8123/` in a browser.
3. Change the port, bind address, render speed, dimensions and layers in `config/dioaddons_webmap-common.toml`.

> **First start:** the Diorama style uses vanilla textures from the official Minecraft client jar, downloaded once from Mojang (about 27 MB, verified with Mojang's SHA-1). The `TOP_DOWN` style needs no download.

## Commands (operators)

| Command | What it does |
|---|---|
| `/webmap status` | Web server, render queue and layers |
| `/webmap render [dimension]` | Render everything again |
| `/webmap clearcache` | Delete all tiles and start over |

HTTP API reference: [docs/webmap-api.md](https://github.com/lorspi/diorama-addons/blob/main/docs/webmap-api.md).

## Works great with

- **[Diorama Addons: Claims](https://modrinth.com/mod/diorama-addons-claims)** — land protection; its claims appear on the web map.
- **[Diorama Addons: Client Map](https://modrinth.com/mod/diorama-addons-clientmap)** — claims, shared exploration and waypoints inside the Diorama map.

Each mod works alone; together they share claims automatically.

## About

Diorama Addons is an unofficial, community-made set of addons inspired by the [Diorama](https://modrinth.com/mod/diorama-map) map mod. It is not affiliated with or endorsed by the Diorama authors. The web interface uses [Leaflet](https://leafletjs.com) (BSD-2-Clause).

Open source under the MIT license — [source code and issues on GitHub](https://github.com/lorspi/diorama-addons).
```

---

## 5. Version 0.1.0 upload

| Field | Value |
|---|---|
| File | `webmap/build/libs/diorama-addons-webmap-1.21.1-0.1.0.jar` (not the `-sources` jar) |
| Version name | `Web Map 0.1.0` |
| Version number | `0.1.0+1.21.1` |
| Release channel | Beta |
| Loaders | NeoForge |
| Game versions | 1.21.1 |
| Environment (CurseForge) | Server |
| Java (CurseForge) | Java 21 |
| Dependencies | None. Optional integration: Diorama Addons: Claims |

### Changelog

```markdown
First release.

- Web map served by the game server (default port 8123), with zoom levels and a dimension selector.
- Diorama style: the world drawn at an angle from block textures, with sun, shadows and outlines. Modded blocks use their own textures. `TOP_DOWN` style available.
- Background rendering at minimum priority with a chunks-per-second cap; only changed areas are redrawn.
- Claims painted on the terrain (colour per owner, name and owner label) when Diorama Addons: Claims is installed.
- Optional player positions (off by default).
- `/webmap status|render|clearcache`; JSON endpoints `/api/info`, `/api/claims`, `/api/players`.
```

## 6. Gallery

| Image | Title | Description |
|---|---|---|
| `docs/images/webmap-claims.png` (set as featured) | Web map with claims | The world in the Diorama style, with claims painted on the terrain. |

Suggested extra captures: a zoomed-out view of a large area, the Nether, and the `TOP_DOWN` style.

## 7. Notes before publishing

- Create the three projects first so the slugs in "Works great with" exist; on CurseForge replace those
  links with the CurseForge project URLs.
- If you link Claims as a dependency, use type **Optional** (Modrinth) / **Optional Dependency**
  (CurseForge).
- The icon uses the Diorama artwork. Ask the Diorama author for permission before publishing it.
