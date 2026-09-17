# TOA Minimap

## 2.7.2 — Map tile integrity fix

- Fixed a serious map rendering bug where a chunk from another part of the world could appear at the wrong coordinates.
- GPU texture identifiers now use the exact chunk X/Z coordinates instead of `Objects.hash(chunkX, chunkZ)`.
- This prevents texture-ID collisions from causing unrelated terrain tiles to overwrite each other.
- Map cache bumped to `client-v9` so the map regenerates cleanly after upgrading.

TOA Minimap is a Minecraft 26.2 Fabric client minimap and world map built specifically for **The Ones Above**. Terrain is generated from Minecraft's client chunk data and stored in a persistent local cache for a crisp, top-down map without relying on Dynmap imagery.

## Server-only access

Version 2.7.1 adds a two-part server restriction. TOA Minimap only activates when both checks pass:

1. The multiplayer address is `theonesabove.com` or a subdomain such as `play.theonesabove.com` or `minecraft.theonesabove.com`.
2. The server sends the official `toaminimap:auth` handshake through the bundled **TOAMinimapAuth** Paper companion plugin.

If either check fails, TOA Minimap stays dormant: the HUD is hidden, **M** and **X** do not open TOA Minimap screens, and no map chunks are generated or added to the cache.

> This is practical server gating, not unbreakable DRM. Because the client code runs on a player's computer, a deliberately modified client can remove client-side restrictions. The handshake ensures the unmodified official client only activates on a server running the TOA companion plugin.

## Features

- Crisp client-generated top-down terrain at one cached map pixel per Minecraft block.
- Persistent explored-map cache, so previously mapped areas remain available after chunks unload.
- Cartographic colour grading, material variation, roof/cliff relief, cleaner vegetation, and directional shading.
- Heading-up HUD minimap: the player remains centred while the map rotates underneath them.
- Compact live coordinates below the minimap.
- Dedicated full-screen World Map with smooth pan and zoom.
- No entity radar, NPC dots, mob markers, other-player markers, or waypoints.
- Minimap position and size can be edited in-game.

## Controls

- **M** — Edit minimap position and size.
  - Left-drag the minimap to move it.
  - Scroll over the minimap to resize it.
  - Esc saves and closes the editor.
- **X** — Open/close the World Map.
  - Left-drag to pan.
  - Scroll to zoom.
  - X or Esc closes the World Map.

All controls can be rebound in Minecraft's keybind settings.

## Client installation

The client requires:

- Minecraft 26.2
- Fabric Loader
- Fabric API
- Java 25

Place the built `toa-minimap-2.7.1.jar` in the client's `mods` folder.

## Server installation

TOA Minimap 2.7.1 requires the bundled **TOAMinimapAuth** Paper plugin on The Ones Above server.

Build it separately:

```powershell
cd server-plugin
mvn clean package
```

Then copy:

```text
server-plugin/target/toa-minimap-auth-1.0.1.jar
```

into the Paper server's `plugins` folder and restart the server.

The plugin sends a small one-byte authorization payload on:

```text
toaminimap:auth
```

No commands or permissions are required.

## Build the Fabric client

From the repository root:

```powershell
gradle clean build --no-daemon --no-watch-fs
```

The client JAR will be created under:

```text
build/libs/toa-minimap-2.7.1.jar
```

## Map cache

Current map tiles are stored under:

```text
.minecraft/toa-minimap-cache/theonesabove/client-v8/
```

The cache is only updated after the server restriction and handshake have both succeeded.

## 2.7.1

- Restricted TOA Minimap to The Ones Above infrastructure.
- Added hostname validation for `theonesabove.com` and its subdomains.
- Added a mandatory server handshake (`toaminimap:auth`).
- Added the `TOAMinimapAuth` Paper companion plugin source.
- Map generation, HUD rendering, World Map and minimap editor remain disabled until authorization succeeds.
- Authorization is cleared immediately on disconnect/server switch.

## 2.6.1

- X closes the World Map as well as opening it and respects a rebound World Map key.
- Visible/new map chunks are prioritised ahead of background prefetch work.
- Moving into a new chunk discards stale queued work from the previous area.
- Client map generation scans more frequently while keeping a strict generation budget to reduce movement hitching.
- Improved fill-in when entering a previously unmapped area.

## License

Copyright © 2026 The Ones Above. All rights reserved. See `LICENSE` for the repository's licensing terms.
