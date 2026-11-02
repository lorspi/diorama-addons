# Third-party code and assets

Any copied or adapted code must be listed here with its origin, license and copyright notice.

| Component | Origin | License | Used in |
|---|---|---|---|
| Diorama map style and light model (sun/sky light, sky occlusion, crease occlusion, height shade, outlines, colour grade and default style values), adapted in `render-core` to a top-down view and to an angled view (camera pitch, top and south faces, outline depth, shadow distance, occlusion levels) | https://github.com/SunkenInTime/diorama (v0.6.0) | MIT, Copyright (c) 2026 Dara Adedeji. Full text in `render-core/src/main/resources/META-INF/licenses/diorama-LICENSE.txt` | `render-core` (used by `claims` and `webmap`) |
| Diorama English strings (`assets/diorama/lang/en_us.json`), translated to Spanish, and the Diorama icon artwork used in the pack icon | https://github.com/SunkenInTime/diorama (v0.7.0) | MIT, Copyright (c) 2026 Dara Adedeji. Full text in `contents/diorama-es-pack/diorama-LICENSE.txt` | Spanish resource pack (`contents/diorama-es-pack`) |
| Leaflet 1.9.4 (`leaflet.js`, `leaflet.css`, images) | https://leafletjs.com, npm package `leaflet@1.9.4` | BSD-2-Clause, (c) 2010-2023 Volodymyr Agafonkin, (c) 2010-2011 CloudMade. Full text in `webmap/src/main/resources/web/vendor/leaflet/LICENSE` | `webmap` |

## Not redistributed

- **Minecraft client jar.** The web map can download the official client jar from Mojang's servers to read
  block textures. It is fetched on the server that runs the mod, verified against Mojang's SHA-1 and never
  included in this repository or in the mod jars.
