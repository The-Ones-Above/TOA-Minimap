# TOA Minimap

**TOA Minimap** is a Fabric client-side minimap and world map built specifically for **The Ones Above** Minecraft server.

It provides a clean, lightweight top-down map generated directly from Minecraft world data, with persistent local map caching, smooth world-map navigation, configurable HUD placement, and server-side authorization so the mod only functions on approved The Ones Above servers.

> **Current version:** 2.7.0  
> **Minecraft:** 26.2  
> **Loader:** Fabric  
> **Java:** 25

---

## Features

### Minimap

- Crisp top-down terrain rendering generated from Minecraft block data
- Heading-up minimap: the world rotates as the player turns
- Persistent local map cache
- Small player marker with white centre and dark outline
- Compact coordinate display
- Adjustable minimap position and size
- No entity radar
- No mobs, NPCs, dropped items, or other-player markers

### World Map

Press **X** to open the full World Map.

- Smooth pan and zoom
- North-up world view
- Persistent previously explored terrain
- Mouse-wheel zooming
- Mouse drag panning
- Player position indicator
- Press **X** again or **Esc** to close

### Minimap Editor

Press **M** to enter minimap edit mode.

While editing:

- Drag the minimap to reposition it
- Scroll over the minimap to resize it
- Press **Esc** to save and exit

---

## Controls

| Key | Action |
|---|---|
| **M** | Edit minimap position and size |
| **X** | Open / close World Map |
| **Esc** | Close editor or World Map |
| **Mouse drag** | Move minimap in edit mode / pan World Map |
| **Mouse wheel** | Resize minimap in edit mode / zoom World Map |

Key bindings can be changed through Minecraft's normal Controls menu.

---

## Server-Locked Access

TOA Minimap is intentionally restricted to **The Ones Above** servers.

The mod only enables when both of the following checks succeed:

1. The player is connected to `theonesabove.com` or an approved subdomain.
2. The server responds to the TOA Minimap authorization handshake.

If authorization fails, TOA Minimap disables:

- minimap rendering
- World Map access
- minimap editing
- map generation
- local cache updates

Authorization is cleared when the player disconnects or changes server.

### Companion Server Plugin

The server-side authorization plugin is included in:

```text
server-plugin/
```

Build it with:

```powershell
cd server-plugin
mvn clean package
```

The resulting JAR will be located in:

```text
server-plugin/target/
```

Place the built `toa-minimap-auth` JAR in the Paper server's:

```text
plugins/
```

directory and restart the server.

---

## Installation

### Client

Install:

- Minecraft 26.2
- Fabric Loader
- Fabric API
- TOA Minimap

Place the TOA Minimap JAR in:

```text
.minecraft/mods/
```

### Server

The server must also have the **TOAMinimapAuth** companion Paper plugin installed for the client mod to activate.

---

## Building From Source

### Client Mod

From the project root:

```powershell
gradle clean build --no-daemon --no-watch-fs
```

The compiled JAR will be generated under:

```text
build/libs/
```

### Server Plugin

From:

```text
server-plugin/
```

run:

```powershell
mvn clean package
```

The server plugin targets Paper 26.2 and Java 25.

---

## Map Rendering

TOA Minimap does **not** use Dynmap images.

Terrain is generated directly from Minecraft world/chunk data and converted into a persistent top-down map.

The renderer includes:

- block-aware map colours
- terrain-height shading
- directional lighting
- material variation
- improved roof and road separation
- foliage and water styling
- reduced vegetation noise
- top-surface sampling
- persistent client-side map tiles

The goal is a clean, readable map while keeping the visual style close to Minecraft itself.

---

## Map Cache

Previously generated map data is saved locally so explored areas remain available after chunks unload.

The current cache format is stored under:

```text
.minecraft/toa-minimap-cache/
```

Map cache data may be regenerated between major renderer updates.

If the map ever displays outdated or corrupted terrain, close Minecraft and delete the TOA Minimap cache folder. It will rebuild automatically the next time those areas are loaded.

---

## Performance

TOA Minimap prioritizes chunks closest to the player and keeps map generation within a small client-side time budget to avoid affecting normal gameplay.

The renderer:

- prioritizes visible terrain
- drops stale queued work after movement
- caches completed map tiles
- avoids repeatedly rebuilding unchanged terrain
- keeps World Map rendering separate from map generation

---

## Privacy

TOA Minimap does not provide player tracking, entity radar, or hidden-world information.

The client only maps world data that Minecraft itself has provided to the player.

The authorization system exists solely to restrict TOA Minimap functionality to approved The Ones Above servers.

---

## Version 2.7.0

### Server Authorization

- Added The Ones Above server restriction
- Added hostname validation
- Added server authorization handshake
- Added companion Paper authorization plugin
- Minimap remains disabled until authorization succeeds
- World Map, map generation, cache updates, and editor are disabled on unauthorized servers
- Authorization resets automatically after disconnecting or switching servers

### Previous Improvements

Recent versions also introduced:

- persistent client-side map caching
- faster nearby-chunk generation
- smoother World Map pan and zoom
- improved surface sampling
- cartographic terrain shading
- cleaner material colouring
- improved player markers
- editable minimap position and scale
- dedicated World Map screen
- removal of Dynmap image rendering

---

## Project Structure

```text
TOA-Minimap/
├── src/
│   └── client/
├── server-plugin/
│   ├── src/
│   └── pom.xml
├── build.gradle
├── gradle.properties
├── settings.gradle
├── README.md
└── LICENSE.md
```

---

## License

TOA Minimap is **proprietary source-available software**.

The source code may be publicly visible, but that does **not** grant permission to copy, modify, redistribute, repackage, sell, fork, or use substantial portions of the project in another mod or project without explicit written permission from **The Ones Above**.

See:

```text
LICENSE.md
```

for the full license terms.

---

## The Ones Above

TOA Minimap is developed for **The Ones Above** Minecraft server.

Minecraft and Mojang are trademarks of Microsoft/Mojang Studios.  
TOA Minimap is not affiliated with or endorsed by Mojang Studios or Microsoft.
