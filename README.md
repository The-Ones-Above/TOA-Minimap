# TOA Minimap

**TOA Minimap** is a Fabric minimap and world map built specifically for **The Ones Above** Minecraft server.

It renders a persistent top-down map directly from Minecraft chunk data, provides a heading-up HUD minimap and full World Map, and uses a Paper companion plugin for server authorization, Citizens NPC markers, and item-based access.

> **Client version:** 2.10.3  
> **Server companion:** 1.1.0  
> **Minecraft:** 26.2  
> **Loader:** Fabric  
> **Java:** 25

---


## 2.10.0

- Citizens / TOAShops NPC markers are now tiny solid yellow dots instead of cross-shaped markers.
- Marker smoothing from 2.8.3 is unchanged.

## 2.8.3

- Reworked minimap entity markers around a frame-rate-independent smoothing pipeline.
- Citizens/TOAShops NPC markers now ignore tiny positional jitter and remain stable while stationary.
- Other-player markers interpolate smoothly between client entity updates.
- Marker positions remain floating-point until the final GUI transform, removing whole-pixel stepping.
- NPC and other-player dots remain clean solid markers with no outline.
- Only the local-player marker keeps the dark outline.
- Large teleports snap immediately instead of slowly sliding across the minimap.

## 2.8.2

- Fixed the update popup URL opener for Minecraft 26.2 mappings by using the standard Java desktop browser API.

## 2.8.1

- Added an automatic GitHub update check against the official latest release.
- When the installed client is outdated, an in-game notice appears once per session.
- Press **Enter** on the notice to open the permanent latest-version download link; **Esc** continues normally.
- Citizens NPC minimap positions are stabilised server-side to remove idle dot jitter.
- NPC dots are clean yellow dots with no dark outline.
- Other-player dots are clean white dots with no dark outline.
- Only the local player marker keeps the black contrast outline.

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
- TOA Minimap 2.10.0

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

## 2.8.1

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


## World Map zones and discovered territory

TOAZones regions are shown on the **full World Map only**. They are intentionally not drawn on the HUD minimap.

The client applies a strict discovery mask:

- A zone is rendered only over map chunks already stored in that player's local TOA Minimap cache.
- Undiscovered chunks do not reveal zone fill, borders, names or the zone's full extent.
- Zone names appear only after the player has discovered the chunk containing the zone centre.
- Safe/spawn-style areas are green.
- Property/plot-style areas are blue.
- Faction/territory/claim-style areas are red.
- Other TOAZones regions such as city/harbour/custom regions use gold.

This means the World Map can show City, Spawn and any faction property/territory represented by TOAZones without revealing places the player has never explored.



## 2.10.0 World Map performance, factions and minimap compass

- Full World Map now uses large 256×256-block GPU region atlases instead of one draw call per 16×16 chunk.
- Fast panning and zooming no longer has to submit hundreds or thousands of individual chunk textures.
- Region textures are built asynchronously and cached after first use.
- TOAFactions HQ land, territories, properties and turfs can now be shown on the World Map.
- All faction-related areas share the same map colour.
- Faction names are not permanently printed over properties/territories.
- Hover a discovered faction area to see which faction owns it.
- Turfs may display their own turf name and also expose ownership on hover.
- Faction overlays obey the same discovered-map mask as TOAZones overlays.
- Added a tiny N/E/S/W edge compass to the HUD minimap.
- Compass positions are derived directly from local player yaw every render frame for immediate response.



## 2.10.1 visual cleanup

- Removed permanent black background boxes from TOAZones labels.
- Removed permanent black background boxes from turf labels.
- Zone and turf names now use clean floating text with a subtle shadow.
- Citizens / TOAShops NPC markers now use a small antialiased circular yellow texture.
- NPC markers have a thin black outline while retaining the existing smooth marker tracking.
- Other-player markers remain plain white without an outline.
- The local-player marker remains white with its existing dark outline.


## 2.10.2 marker and faction colour update

- Citizens / TOAShops NPC markers are square again.
- NPC marker uses a yellow 2×2 centre with a one-pixel black border around it.
- Removed the rounded NPC marker texture.
- Faction HQs, territories, properties and owned turfs now use the owning faction's configured TOAFactions colour.
- All areas owned by the same faction therefore share the same map colour.
- Unclaimed turfs use a neutral grey.
- Faction ownership is still revealed on hover rather than permanently printing faction names over properties/territories.


## 2.10.3 marker and ownership tooltip polish

- NPC and remote-player minimap markers now use the same compact square size.
- Both marker types now have a thin black outline.
- NPC centre remains yellow.
- Remote-player centre remains white.
- Faction hover tooltips keep “Owned by” neutral but render the faction name itself using that faction's configured TOAFactions colour.
