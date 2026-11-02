# Contenido para Modrinth y CurseForge

Material para publicar los tres mods. `claims-api` y `render-core` no se publican aparte: van dentro de los
otros jars.

```
contents/
├─ claims/      listing.en.md (para publicar) · listing.es.md (traducción de referencia)
├─ webmap/      listing.en.md · listing.es.md
├─ clientmap/   listing.en.md · listing.es.md
├─ icons/      claims-icon.png · webmap-icon.png · clientmap-icon.png (finales, opción G) · icons-preview.png
│  └─ options/  opciones descartadas para Claims (A–F), hoja comparativa y vista previa de los glifos
└─ _source/     icono original de Diorama y el script que genera los iconos
```

## Resource pack: Diorama en español

`diorama-es-pack/` es un resource pack con la traducción al español de Diorama 0.7.0 (370 claves; los
textos de `en_us.json` del jar). Usa un solo `es_es.json`, que al empaquetar se copia a todas las variantes
de español de Minecraft (`es_mx`, `es_ar`, `es_cl`, `es_ec`, `es_uy`, `es_ve`). El icono (`pack.png`) es la
opción G en dorado con la bandera de Colombia en el recuadro.

```bash
python -I _source/make_icons.py diorama-es-pack pack gold
python -I _source/make_lang_pack.py diorama-es-pack diorama-es-pack.zip
```

Cuando salga una versión nueva de Diorama, compara su `en_us.json` con `es_es.json` y traduce las claves
nuevas.

## Iconos

Cada icono es la ilustración de Diorama con un borde redondeado y una pestaña con un glifo pixel-art:
bandera (Claims), globo terráqueo (Web Map) y mapa con chincheta (Client Map). Todos son PNG de 512×512 con
esquinas transparentes y pesan menos de 256 KiB (límite de Modrinth).

| Opción | Pestaña | Color de ejemplo |
|---|---|---|
| A | Centrada abajo, sube desde el borde | Turquesa |
| B | Insignia en la esquina inferior derecha | Pizarra con placa dorada |
| C | Carpeta: pestaña que sobresale arriba a la izquierda | Dorado |
| D | Marcapáginas que cuelga del borde superior derecho | Rojo |
| E | Banda ancha a lo largo del borde inferior | Turquesa |
| F | Medallón redondo en la esquina superior izquierda | Dorado |
| **G (elegida)** | El borde crece en un bloque en la esquina inferior derecha; la ilustración lo rodea con esquinas redondeadas | Dorado / azul / turquesa |

El color del borde es independiente de la forma: cualquier opción puede usar cualquier paleta (`teal`,
`gold`, `slate`, `red`, `blue`).

Regenerar:

```bash
python -I _source/make_icons.py icons/options
```

Generar los tres iconos finales con la opción elegida, que quedan en `icons/`:

```bash
python -I _source/make_icons.py icons final G
```

Paletas de los finales (en `make_icons.py`, función `main`): Claims dorado, Web Map azul, Client Map
turquesa.
