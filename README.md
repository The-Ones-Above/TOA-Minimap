# TOA Minimap

**TOA Minimap** is a Fabric minimap and world map built specifically for **The Ones Above** Minecraft server.

It renders a persistent top-down map directly from Minecraft chunk data, provides a heading-up HUD minimap and full World Map, and uses a Paper companion plugin for server authorization, Citizens NPC markers, and item-based access.

> **Client version:** 2.8.0  
> **Server companion:** 1.1.0  
> **Minecraft:** 26.2  
> **Loader:** Fabric  
> **Java:** 25

---

## Features

### Minimap

- Crisp client-generated top-down terrain
- Heading-up view: the terrain rotates as the player turns
- Persistent local map cache
- Small local-player marker
- **Other real players shown as white dots**
- **Citizens NPCs shown as yellow dots**
- TOAShops Citizens-backed NPCs are included automatically
- Player/NPC dots are shown on the **minimap only**
- Compact coordinates
- Adjustable position and size

### World Map

Press **X** while carrying a **City Map**.

- Smooth pan and zoom
- North-up map
- Persistent explored terrain
- Mouse-wheel zoom
- Mouse drag panning
- No NPC dots
- No other-player dots
- Press **X** again or **Esc** to close

### Item-Based Access

The minimap and World Map are no longer available simply because the mod is installed.

- **Navigator's Compass** — required for the HUD minimap and minimap editor
- **City Map** — required to open the World Map

The items use persistent server-side IDs. Ordinary vanilla compasses and maps do **not** grant access.

The player only needs the corresponding TOA item somewhere in their normal inventory; it does not have to be held in hand.

---

## Controls

| Key | Action |
|---|---|
| **M** | Edit minimap position/size — requires Navigator's Compass |
| **X** | Open/close World Map — requires City Map |
| **Esc** | Close editor or World Map |
| **Mouse drag** | Move minimap in editor / pan World Map |
| **Mouse wheel** | Resize minimap in editor / zoom World Map |

Key bindings can be changed through Minecraft's normal Controls menu.

---

## Giving the Access Items

The Paper companion provides an admin command:

```text
/toaminimap give compass [player]
/toaminimap give map [player]
/toaminimap give both [player]
```

Permission:

```text
toaminimap.admin
```

The permission defaults to server operators.

The items currently use the vanilla Compass and Map appearances with custom names/lore and persistent TOA IDs. Their visual models can later be replaced through Oraxen or a resource pack without changing the access system.

---

## Citizens / TOAShops Markers

The server companion detects spawned Citizens NPCs and sends only their nearby positions/UUIDs to authorised TOA Minimap clients.

On the HUD minimap:

- **Yellow** — Citizens NPC
- **White** — other real player

TOAShops NPCs created through its Citizens integration are therefore detected automatically.

Markers are intentionally **not rendered on the World Map**.

---

## Server-Locked Access

TOA Minimap only activates when both checks succeed:

1. The player is connected to `theonesabove.com` or one of its subdomains.
2. The Paper companion responds to the TOA Minimap authorization handshake.

Outside an authorised TOA server, map generation, cache updates, minimap rendering and World Map access are disabled.

### Network channels

```text
toaminimap:auth   - authorization request/response
toaminimap:state  - Navigator's Compass / City Map inventory state
toaminimap:npcs   - nearby Citizens marker snapshot
```

---

## Installation

### Client

Install:

- Minecraft 26.2
- Fabric Loader
- Fabric API
- TOA Minimap 2.8.0

Place the client JAR in:

```text
.minecraft/mods/
```

### Server

Build and install the companion Paper plugin from `server-plugin/`.

```powershell
cd server-plugin
mvn clean package
```

Place the resulting JAR from:

```text
server-plugin/target/
```

into the server's `plugins/` directory and restart Paper.

Citizens is an optional soft dependency. If Citizens is installed, its NPCs are published to the minimap automatically.

---

## Building the Client

From the project root:

```powershell
gradle clean build --no-daemon --no-watch-fs
```

The client JAR is generated in:

```text
build/libs/
```

---

## Map Cache

Generated terrain is saved under:

```text
.minecraft/toa-minimap-cache/
```

This allows explored terrain to remain visible after chunks unload.

If map data ever becomes outdated or corrupted, close Minecraft and delete the TOA Minimap cache directory. It will regenerate as chunks are received again.

---

## 2.8.0

- Added Navigator's Compass requirement for the HUD minimap
- Added City Map requirement for the full World Map
- Added `/toaminimap give` admin commands
- Added server-authoritative item-state syncing
- Added white dots for nearby real players on the minimap
- Added yellow dots for Citizens NPCs on the minimap
- TOAShops Citizens NPCs are detected automatically
- NPC/player dots remain hidden from the World Map
- Map generation pauses when the player owns neither access item
- Retains The Ones Above hostname + server handshake restriction

---

## License

TOA Minimap is proprietary source-available software. Public source visibility does not grant permission to copy, redistribute, repackage, fork, sell, or use substantial portions of the project without written permission from **The Ones Above**.

See `LICENSE` for the full terms.

Minecraft and Mojang are trademarks of Microsoft/Mojang Studios. TOA Minimap is not affiliated with or endorsed by Mojang Studios or Microsoft.
