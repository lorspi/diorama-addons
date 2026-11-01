# Suite de mods NeoForge: claims + mapa web + mapa de cliente

> Cómo usarlo: crea una carpeta vacía para el repositorio, guarda este archivo como `CLAUDE.md` en la raíz y abre Claude Code ahí. Primer mensaje sugerido: *"Lee CLAUDE.md. Haz la Fase 0 y detente a pedirme confirmación antes de la Fase 1."*

## 1. Objetivo

Construir, en **un solo repositorio (monorepo)**, varios mods de Minecraft **independientes entre sí** (cada uno genera su propio `.jar` y funciona solo), pero que se integran de forma opcional cuando coinciden en el mismo servidor.

| Módulo | Qué es | Dónde corre |
|---|---|---|
| `claims-api` | Interfaces y modelos pequeños para consultar claims (sin lógica de juego) | Librería |
| `claims` | Protección de terreno con **interfaz gráfica**, amigos y permisos | Servidor (obligatorio) + cliente (opcional, para la GUI) |
| `webmap` | Mapa web que sirve tiles del mundo y muestra los claims | Solo servidor |
| `clientmap` | Mapa en el juego con claims, mapa revelado compartido y ubicaciones compartidas | Servidor + cliente (ver Fase 3) |

Las ideas de fondo: reclamar zonas para el jugador y sus amigos, ver el mapa del mundo en el navegador, y que el mapa revelado y las ubicaciones se compartan entre todos los jugadores.

## 2. Restricciones técnicas

- **NeoForge `21.1.265` en adelante** (Minecraft 1.21.1). En `neoforge.mods.toml` usa el rango `[21.1.265,)`. Para compilar, fija `21.1.265` en `gradle.properties`.
- **Java 21**, **Gradle** con el plugin **ModDevGradle** (`net.neoforged.moddev`). Comprueba cuál es su versión estable actual antes de fijarla.
- Idiomas: código, comentarios y commits en **inglés**. Textos del juego con archivos de idioma `en_us.json` y `es_es.json`.
- Licencia: **MIT** (open source). Si copias código de otro proyecto, conserva su aviso de licencia y anótalo en `THIRD_PARTY.md`.
- **Nunca inventes APIs de NeoForge ni de Minecraft.** Si no estás seguro de un nombre de clase, evento o método, búscalo en el código fuente de NeoForge 21.1.x (las fuentes descompiladas que genera Gradle) antes de usarlo.

## 3. Estructura del repositorio

```
/
├─ CLAUDE.md
├─ README.md               (descripción, estado de cada mod, capturas)
├─ LICENSE
├─ THIRD_PARTY.md
├─ settings.gradle.kts
├─ gradle.properties       (versiones comunes)
├─ build-logic/            (convenciones de Gradle compartidas)
├─ docs/
│  ├─ ARCHITECTURE.md
│  ├─ claims-permissions.md
│  └─ webmap-api.md
├─ claims-api/
├─ claims/
├─ webmap/
└─ clientmap/
```

Cada mod con `mod_id` propio y prefijo común. **Antes de crear nada, pregúntame el nombre del proyecto** para fijar el prefijo (por ejemplo `<prefijo>_claims`, `<prefijo>_webmap`). Los IDs no deben chocar con mods existentes.

## 4. Principios de independencia

1. Ningún mod depende en **obligatorio** de otro. En `neoforge.mods.toml` las dependencias entre ellos van como `type = "optional"`.
2. La integración se hace por `claims-api` y por detección con `ModList.get().isLoaded(...)`. El código que usa la clase de otro mod va en una clase aparte, **que solo se carga si ese mod está presente**.
3. Un servidor puede instalar solo `claims`, solo `webmap`, o cualquier combinación.
4. Un jugador **sin ningún mod** debe poder entrar a un servidor que tenga `claims`. Todo lo que el servidor necesite del cliente va en canales de red marcados como **opcionales**.
5. La lógica pura (reglas de claims, resolución de permisos, cálculo de tiles) va en clases **sin dependencias de Minecraft**, para poder probarla con tests unitarios.
6. Ante cualquier decisión de diseño no trivial, escribe una nota en `docs/ARCHITECTURE.md` y sigue.

## 5. Reglas de trabajo para Claude Code

- Trabaja **por fases** y detente al final de cada una para que yo la revise. No empieces la siguiente sin mi confirmación.
- Al terminar cada fase: compila (`./gradlew build`), ejecuta los tests y **arranca un servidor de pruebas** (`./gradlew :<modulo>:runServer`). No digas que algo funciona si no lo ejecutaste. Si no pudiste probarlo, dilo.
- Commits pequeños y descriptivos (Conventional Commits: `feat(claims): ...`).
- Nunca confíes en el cliente: toda acción enviada desde la GUI se valida en el servidor (propietario, permisos, límites).
- No bloquees el hilo principal del servidor con trabajo pesado (renderizado, disco, red). Usa hilos propios y vuelve al hilo del servidor con `server.execute(...)` solo cuando haga falta.
- Todo valor configurable va en archivos de configuración (`ModConfigSpec`) con comentarios, no fijo en el código.
- Si algo del documento es ambiguo, haz una suposición razonable, anótala en `docs/ARCHITECTURE.md` y sigue. Pregúntame solo si el error sería caro de deshacer.

## 6. Fase 0: base del repositorio

- Monorepo Gradle con `claims-api`, `claims`, `webmap` y `clientmap` (este último puede quedar vacío por ahora).
- Convenciones comunes en `build-logic` (Java 21, ModDevGradle, versión de NeoForge, nombre de los jars).
- Cada mod arranca vacío, con su `neoforge.mods.toml` y un log de inicio. `./gradlew build` produce un jar por mod.
- GitHub Actions que compila y ejecuta los tests.
- `README.md`, `LICENSE` (MIT), `docs/ARCHITECTURE.md` con la estructura y las decisiones.

**Hecho cuando:** compila limpio, los jars se generan y `runServer` arranca cargando el mod.

## 7. Fase 1: mod `claims`

### 7.1 Modelo

- La unidad de reclamo es el **chunk** (columna completa 16×16), por dimensión.
- Un **Claim** tiene: id, nombre, propietario (UUID), dimensión, conjunto de chunks, miembros y permisos públicos.
- Límites configurables: máximo de chunks por jugador, máximo de claims por jugador, y si los chunks de un mismo claim deben ser contiguos.
- Prohibido reclamar un chunk ya reclamado por otro. Opcional y configurable: distancia mínima al spawn.
- Persistencia con `SavedData` y códecs versionados, de modo que se pueda migrar el formato más adelante.

### 7.2 Amigos y permisos

Permisos como enum (puedes ajustar esta lista tras revisar el juego):

`BREAK_BLOCKS`, `PLACE_BLOCKS`, `INTERACT_BLOCKS` (puertas, botones, palancas), `OPEN_CONTAINERS`, `USE_ITEMS` (cubos, mecheros…), `INTERACT_ENTITIES`, `DAMAGE_ANIMALS`, `PVP`, `EXPLOSIONS`, `FIRE_SPREAD`, `MOB_GRIEFING`, `TRAMPLE_FARMLAND`.

Reglas:

- El **propietario** siempre tiene todos los permisos.
- Cada **miembro** (amigo) tiene permisos propios. Se ofrecen plantillas rápidas (por ejemplo *Visitante*, *Miembro*, *Confiable*) y se puede personalizar permiso por permiso.
- El **público** (cualquier otro jugador) tiene su propio conjunto de permisos, por defecto todos denegados excepto los que el propietario active.
- Orden de resolución: propietario → permisos propios del miembro → permisos públicos.
- Los operadores pueden ignorar la protección con un comando de administración (`bypass`), y también ver, editar o borrar cualquier claim.
- Documenta todo en `docs/claims-permissions.md`.

### 7.3 Interfaz gráfica (requisito principal)

Pantalla de cliente con:

- **Cuadrícula de chunks** centrada en el jugador (por ejemplo 21×21), con colores: libre, tuyo, de amigos, ajeno, y el chunk actual resaltado.
- Clic para reclamar o liberar un chunk, y arrastrar para seleccionar varios.
- **Panel lateral** con la lista de claims del jugador, renombrar, borrar y ver cuántos chunks usa del límite.
- **Pestaña de miembros:** añadir un amigo (jugador conectado o por nombre), quitarlo, elegir plantilla y editar permisos.
- **Pestaña de permisos públicos.**
- Se abre con una tecla configurable y con `/claims menu`.

Cada acción viaja al servidor como un paquete (`CustomPacketPayload`), el servidor la valida y responde con el estado actualizado. Los textos van con claves de traducción.

### 7.4 Funciona sin el mod en el cliente

Todo debe poder hacerse **por comandos**, para quien no tenga el mod instalado: `/claim`, `/unclaim`, `/claims list`, `/claims trust <jugador>`, `/claims untrust`, `/claims permission ...`, `/claims info`, `/claims admin ...`. Mensajes claros cuando una acción es denegada.

### 7.5 Protección

Cubre como mínimo: romper y colocar bloques, interactuar con bloques y contenedores, usar objetos, interactuar con entidades y dañar animales, explosiones, fuego, fluidos que cruzan el borde del claim, pistones que empujan bloques hacia dentro o fuera, el pisoteo de cultivos, y el daño de mobs al entorno. Busca los eventos correctos de NeoForge 21.1.x (por ejemplo `BlockEvent`, `PlayerInteractEvent`, `ExplosionEvent`, `PistonEvent`) y **verifica sus nombres reales en el código fuente**.

### 7.6 `claims-api`

Interfaces mínimas, sin lógica: consultar el claim en una posición, listar los claims de una dimensión, obtener nombre y propietario, y un evento o callback cuando cambian. Es lo que `webmap` y `clientmap` usan para mostrar los claims sin depender del mod completo.

### 7.7 Tests y criterios de aceptación

- Tests unitarios de la resolución de permisos, de los límites y de la serialización.
- Prueba manual en `runServer` y `runClient` con dos jugadores: reclamar, añadir un amigo, quitar un permiso, comprobar que el amigo no puede romper y que un tercero tampoco.
- Un cliente vanilla puede entrar al servidor.

**Hecho cuando:** se puede reclamar, compartir y configurar desde la GUI y desde comandos, la protección funciona y los datos persisten tras reiniciar.

## 8. Fase 2: mod `webmap`

- Solo servidor. Los jugadores no instalan nada.
- Servidor HTTP embebido (el de la JDK o una librería muy ligera), **puerto por defecto 8123**, configurable en el archivo de configuración junto con la dirección de enlace (`bind`). Si el puerto está ocupado, registra un error claro y **no tumba el servidor**.
- Solo lectura. Sin autenticación en esta versión, así que por defecto no se expone nada sensible.
- Interfaz web con **Leaflet** (o similar), incluida dentro del jar y servida desde el propio mod, con selector de dimensión.
- **Tiles:** se generan en segundo plano desde los archivos de región del mundo, con la menor prioridad posible, y se guardan en una caché en disco. Se vuelven a generar solo los chunks que cambiaron (marcado con eventos de descarga y guardado de chunks). Primero una vista cenital con relieve sombreado; un estilo en ángulo (isométrico) queda como mejora posterior.
- **Endpoints** (documentarlos en `docs/webmap-api.md`): `/` (la web), `/tiles/{dim}/{z}/{x}/{y}.png`, `/api/claims`, `/api/players` y `/api/info`.
- **Claims:** si `claims` está presente (por `claims-api`), se dibujan en el mapa unidos por chunks, con un color por propietario y una etiqueta con el nombre del claim y su dueño. Los miembros y permisos **no** se exponen en la web por defecto. Si `claims` no está, el mapa funciona igual sin esa capa.
- **Jugadores:** mostrar sus posiciones es una opción de configuración, **desactivada por defecto**, porque la web puede estar expuesta a internet.
- Comando de administración `/webmap` para ver el estado, forzar un re-renderizado y vaciar la caché.

**Hecho cuando:** entrar a `http://localhost:8123` muestra el mundo, las zonas ya generadas se ven, los claims aparecen si `claims` está instalado, y el servidor sigue fluido mientras se generan tiles.

## 9. Fase 3: mod `clientmap` (integración con Diorama)

**Decisión tomada:** `clientmap` es un **mod cliente propio que se integra con Diorama** por sus puntos de extensión. No se hace fork ni PR de Diorama, y no se escribe un mapa propio. Esta fase tiene dos partes y **se hacen seguidas, sin detenerte a pedirme confirmación entre ellas**.

### 9.1 Investigación (antes de escribir el mod)

Estudia el proyecto **Diorama** (`https://github.com/SunkenInTime/diorama`, licencia MIT, solo cliente, soporta Fabric, Forge y NeoForge, usa Stonecutter para varias versiones) y escribe `docs/clientmap-research.md` respondiendo:

1. ¿Tiene alguna API pública o punto de extensión para añadir capas, waypoints o datos de mapa desde otro mod?
2. ¿Cómo hace la integración con FTB Chunks para dibujar claims? ¿Se puede replicar con `claims-api`?
3. ¿Cómo añadir al menú de clic derecho del mapa una opción "Reclamar / liberar chunk"?
4. ¿Cómo guarda los chunks explorados y los waypoints, y cómo se podrían alimentar con datos del servidor?
5. ¿Qué versión de Diorama es compatible con NeoForge 21.1.x?

Termina el documento con el plan de integración concreto: qué puntos de extensión de Diorama vas a usar para cada capacidad y qué versiones de Diorama vas a soportar.

**Si Diorama no ofrece un punto de extensión para alguna capacidad**, no te detengas ni cambies de enfoque. Resuélvelo dentro del mismo, en este orden:

1. Usa lo que Diorama ya soporta (por ejemplo waypoints compartidos por chat, el protocolo de rastreo de jugadores compatible con Xaero o los códigos de chat del servidor).
2. Si no basta, usa **Mixin** sobre clases concretas de Diorama, aislado en una capa de compatibilidad (`clientmap/compat/diorama`), declarado como opcional y acotado a las versiones de Diorama que hayas probado.
3. Lo que siga sin poder hacerse, déjalo documentado en `docs/clientmap-research.md` como limitación conocida, con la razón, y continúa con el resto.

Toda decisión que tomes aquí se anota en `docs/ARCHITECTURE.md`. Sin Diorama instalado, `clientmap` debe cargar sin errores y simplemente no hacer nada.

### 9.2 Implementación

Requisitos funcionales:

- El mapa muestra los **claims** (color por propietario, tooltip con nombre y dueño) y permite **reclamar o liberar** un chunk desde el propio mapa, usando los mismos paquetes validados por el servidor que la GUI de `claims`.
- **Mapa revelado compartido:** el servidor guarda qué chunks han sido explorados (por todos, o por grupo; que sea configurable) y lo sincroniza a los clientes con paquetes opcionales, por lotes y con límite de tasa.
- **Ubicaciones compartidas:** waypoints compartidos por todos, con propietario y control de quién puede editarlos o borrarlos, y posiciones de jugadores con opción de **privacidad** por jugador (visible u oculta).
- Un cliente sin el mod sigue pudiendo entrar al servidor.

## 10. Open source

- `README.md` con descripción, capturas, instalación y comandos de cada mod; `CONTRIBUTING.md` y plantillas de issues.
- Versión semántica por mod, con tags por módulo (`claims-v0.1.0`) y un `CHANGELOG.md`.
- Al final, un workflow de GitHub Actions para publicar los jars en las Releases (publicar en Modrinth o CurseForge queda para después).

## 11. Decisiones que me tienes que confirmar

- Nombre del proyecto y prefijo de los mod IDs: Diorama Addons. Los prefijos que sugieras.
- Si el límite de chunks y claims por jugador es global o por rango: Por jugador. Cada jugador tiene sus claims y puede invitar a otros a que tenga permiso en esos claims. Si un jugador es miembro de claims de otros jugadores, no afecta sus límites. A cada jugador que invites le puedes configurar sus permisos: Romper, interactuar, acceder a inventarios, etc. Todo estos debe tener GUI.
- En el mapa revelado compartido: ¿global para todos o por grupo de amigos?: Que en el servidor se pueda configurar si está deshabilitado, global o por grupos. En el servidor también se puede configurar se las ubiacaciones compartidas están deshabilitadas, globales o por grupos de amigos.