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
