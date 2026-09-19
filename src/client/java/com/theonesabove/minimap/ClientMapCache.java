package com.theonesabove.minimap;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.LevelChunk;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.*;

/** Persistent client-generated map cache. One cached pixel equals one world block. */
public final class ClientMapCache implements AutoCloseable {
    public static final int TILE_BLOCKS = 16;
    public static final int REGION_CHUNKS = 16;
    public static final int REGION_BLOCKS = TILE_BLOCKS * REGION_CHUNKS;
    private static final int CACHE_MAGIC = 0x544F4135;
    private static final int SCAN_INTERVAL_TICKS = 2;
    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final int REFRESH_RADIUS_CHUNKS = 2;
    private static final long MIN_CHUNK_REFRESH_MS = 900L;
    private static final int MAX_GENERATE_PER_TICK = 5;
    private static final long GENERATION_BUDGET_NS = 2_250_000L;

    private final MinimapConfig config;
    private final Map<ChunkKey, Entry> entries = new ConcurrentHashMap<>();
    private final Deque<ChunkKey> generateQueue = new ConcurrentLinkedDeque<>();
    private final Set<ChunkKey> queued = ConcurrentHashMap.newKeySet();
    private final Set<ChunkCoord> knownChunks = ConcurrentHashMap.newKeySet();
    private final Set<RegionCoord> knownRegions = ConcurrentHashMap.newKeySet();
    private final Map<RegionKey, RegionEntry> regionEntries = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "TOA-Minimap-MapIO");
        t.setDaemon(true);
        return t;
    });

    private final Path cacheRoot;
    private int ticks;
    private int lastCenterChunkX = Integer.MIN_VALUE;
    private int lastCenterChunkZ = Integer.MIN_VALUE;

    public ClientMapCache(MinimapConfig config) {
        this.config = config;
        this.cacheRoot = FabricLoader.getInstance().getGameDir()
                .resolve("toa-minimap-cache")
                .resolve("theonesabove")
                .resolve("client-v9");
        indexDiskCacheAsync();
    }

    public void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        ticks++;
        int centerChunkX = ((int)Math.floor(mc.player.getX())) >> 4;
        int centerChunkZ = ((int)Math.floor(mc.player.getZ())) >> 4;

        // When the player crosses into a new chunk, throw away stale background work
        // from the previous area. The renderer immediately re-queues the chunks that
        // are actually visible, so new terrain fills around the player first instead
        // of waiting behind hundreds of old prefetch jobs.
        if (centerChunkX != lastCenterChunkX || centerChunkZ != lastCenterChunkZ) {
            generateQueue.clear();
            queued.clear();
            lastCenterChunkX = centerChunkX;
            lastCenterChunkZ = centerChunkZ;
            queueAllLoadedAroundPlayer(mc);
        } else if (ticks % SCAN_INTERVAL_TICKS == 0) {
            queueAllLoadedAroundPlayer(mc);
        }

        // Previously-discovered chunks must not become permanent snapshots.
        // Re-sample a small loaded area around the player roughly once per second
        // so newly-built roads, roofs, walls and removed blocks appear on both the
        // HUD minimap and the persistent World Map.
        if (ticks % REFRESH_INTERVAL_TICKS == 0) {
            queueRefreshAroundPlayer(mc);
        }

        long start = System.nanoTime();
        int generated = 0;
        while (generated < MAX_GENERATE_PER_TICK && System.nanoTime() - start < GENERATION_BUDGET_NS) {
            ChunkKey key = generateQueue.poll();
            if (key == null) break;
            queued.remove(key);
            if (!mc.level.hasChunk(key.x, key.z)) continue;
            generate(mc.level, key);
            generated++;
        }
    }

    private void queueAllLoadedAroundPlayer(Minecraft mc) {
        int centerChunkX = ((int)Math.floor(mc.player.getX())) >> 4;
        int centerChunkZ = ((int)Math.floor(mc.player.getZ())) >> 4;
        int radius = config.prefetchChunkRadius;
        // Queue closest chunks first. This makes the visible minimap fill before distant chunks.
        for (int ring = 0; ring <= radius; ring++) {
            for (int dz = -ring; dz <= ring; dz++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int cx = centerChunkX + dx;
                    int cz = centerChunkZ + dz;
                    if (!mc.level.hasChunk(cx, cz)) continue;

                    ChunkKey key = new ChunkKey(cx, cz);
                    Entry e = entries.computeIfAbsent(key, k -> new Entry());
                    boolean neverGenerated = e.generatedAt == 0 && !knownChunks.contains(new ChunkCoord(cx, cz));
                    if (neverGenerated) queueGenerate(key, false);
                }
            }
        }
    }

    private void queueRefreshAroundPlayer(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        int centerChunkX = ((int)Math.floor(mc.player.getX())) >> 4;
        int centerChunkZ = ((int)Math.floor(mc.player.getZ())) >> 4;
        long now = System.currentTimeMillis();

        // Closest chunks first. A 5x5 area is enough to catch normal building
        // activity without continually re-scanning the entire render distance.
        for (int ring = 0; ring <= REFRESH_RADIUS_CHUNKS; ring++) {
            for (int dz = -ring; dz <= ring; dz++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;

                    int cx = centerChunkX + dx;
                    int cz = centerChunkZ + dz;
                    if (!mc.level.hasChunk(cx, cz)) continue;

                    ChunkKey key = new ChunkKey(cx, cz);
                    Entry entry = entries.computeIfAbsent(key, k -> new Entry());

                    // Don't waste work on a chunk we only know from disk but have
                    // not actually loaded near the player yet.
                    if (!knownChunks.contains(new ChunkCoord(cx, cz))
                            && entry.generatedAt == 0) {
                        continue;
                    }

                    if (now - entry.lastRefreshQueuedAt < MIN_CHUNK_REFRESH_MS) continue;
                    entry.lastRefreshQueuedAt = now;
                    queueGenerate(key, false);
                }
            }
        }
    }

    public TileTexture texture(Minecraft mc, int chunkX, int chunkZ) {
        ChunkKey key = new ChunkKey(chunkX, chunkZ);
        Entry entry = entries.computeIfAbsent(key, k -> new Entry());
        entry.lastAccess = System.currentTimeMillis();

        // A refreshed chunk may already have a GPU texture. Always consume newly
        // generated pixels first so the old texture cannot hide an update.
        if (entry.readyPixels != null) {
            int[] pixels = entry.readyPixels;
            entry.readyPixels = null;
            entry.pixels = pixels;
            upload(key, entry, pixels);
            if (entry.textureId != null) {
                return new TileTexture(entry.textureId, TILE_BLOCKS, TILE_BLOCKS);
            }
        }

        if (entry.textureId != null) {
            return new TileTexture(entry.textureId, TILE_BLOCKS, TILE_BLOCKS);
        }

        if (!entry.diskChecked) {
            entry.diskChecked = true;
            Path path = fileFor(key);
            if (Files.isRegularFile(path)) {
                entry.loadingDisk = true;
                CompletableFuture.runAsync(() -> {
                    try {
                        entry.readyPixels = readPixels(path);
                        entry.pixels = entry.readyPixels;
                        if (entry.readyPixels != null) registerKnownChunk(key.x, key.z);
                    } catch (Exception ignored) {
                    } finally {
                        entry.loadingDisk = false;
                    }
                }, io);
            }
        }

        if (!entry.loadingDisk && mc.level != null && mc.level.hasChunk(chunkX, chunkZ)) {
            // A tile requested by the active minimap/world-map view is urgent. Put it
            // at the front of the deque so visible black gaps fill before background
            // prefetch work.
            queueGenerate(key, true);
        }
        return null;
    }

    public Set<ChunkCoord> knownChunks() {
        return knownChunks;
    }

    public Set<RegionCoord> knownRegions() {
        return knownRegions;
    }

    /** True only after this client has actually generated or loaded this map chunk. */
    public boolean isKnownChunk(int chunkX, int chunkZ) {
        return knownChunks.contains(new ChunkCoord(chunkX, chunkZ));
    }


    /**
     * World Map atlas: 16x16 map chunks are combined into one 256x256 GPU texture.
     * This reduces a large zoomed-out map from hundreds/thousands of draw calls to
     * a small number of region draw calls.
     */
    public RegionTexture regionTexture(Minecraft mc, int regionX, int regionZ) {
        RegionKey key = new RegionKey(regionX, regionZ);
        RegionEntry region = regionEntries.computeIfAbsent(key, ignored -> new RegionEntry());
        region.lastAccess = System.currentTimeMillis();

        if (region.readyPixels != null) {
            int[] pixels = region.readyPixels;
            region.readyPixels = null;
            uploadRegion(key, region, pixels);
            region.dirty = false;
        }

        if ((region.textureId == null || region.dirty) && !region.loading) {
            region.loading = true;
            CompletableFuture.runAsync(() -> buildRegionPixels(key, region), io);
        }

        if (region.textureId == null) return null;
        return new RegionTexture(region.textureId, REGION_BLOCKS, REGION_BLOCKS);
    }

    private void buildRegionPixels(RegionKey key, RegionEntry region) {
        try {
            int[] regionPixels = new int[REGION_BLOCKS * REGION_BLOCKS];
            boolean any = false;

            int baseChunkX = key.x * REGION_CHUNKS;
            int baseChunkZ = key.z * REGION_CHUNKS;

            for (int localChunkZ = 0; localChunkZ < REGION_CHUNKS; localChunkZ++) {
                for (int localChunkX = 0; localChunkX < REGION_CHUNKS; localChunkX++) {
                    int chunkX = baseChunkX + localChunkX;
                    int chunkZ = baseChunkZ + localChunkZ;
                    if (!knownChunks.contains(new ChunkCoord(chunkX, chunkZ))) continue;

                    ChunkKey chunkKey = new ChunkKey(chunkX, chunkZ);
                    Entry entry = entries.computeIfAbsent(chunkKey, ignored -> new Entry());
                    int[] pixels = entry.pixels;

                    if (pixels == null) {
                        Path path = fileFor(chunkKey);
                        if (Files.isRegularFile(path)) {
                            try {
                                pixels = readPixels(path);
                                entry.pixels = pixels;
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    if (pixels == null) continue;
                    any = true;

                    int destX = localChunkX * TILE_BLOCKS;
                    int destY = localChunkZ * TILE_BLOCKS;
                    for (int y = 0; y < TILE_BLOCKS; y++) {
                        System.arraycopy(
                                pixels, y * TILE_BLOCKS,
                                regionPixels, (destY + y) * REGION_BLOCKS + destX,
                                TILE_BLOCKS
                        );
                    }
                }
            }

            if (any) region.readyPixels = regionPixels;
        } finally {
            region.loading = false;
        }
    }

    private void uploadRegion(RegionKey key, RegionEntry region, int[] pixels) {
        try {
            NativeImage nativeImage = new NativeImage(REGION_BLOCKS, REGION_BLOCKS, false);
            for (int y = 0; y < REGION_BLOCKS; y++) {
                int row = y * REGION_BLOCKS;
                for (int x = 0; x < REGION_BLOCKS; x++) {
                    nativeImage.setPixel(x, y, pixels[row + x]);
                }
            }

            Identifier id = Identifier.fromNamespaceAndPath(
                    ToaMinimapClient.MOD_ID,
                    "world_region/x_" + key.x + "_z_" + key.z
            );

            if (region.textureId != null) {
                try { Minecraft.getInstance().getTextureManager().release(region.textureId); }
                catch (Exception ignored) {}
            }

            DynamicTexture texture = new DynamicTexture(() -> "TOA Minimap world map region", nativeImage);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            texture.upload();
            region.texture = texture;
            region.textureId = id;
        } catch (Exception ex) {
            System.err.println("[TOA Minimap] Failed to upload world region " + key + ": " + ex.getMessage());
        }
    }

    private void registerKnownChunk(int chunkX, int chunkZ) {
        knownChunks.add(new ChunkCoord(chunkX, chunkZ));
        knownRegions.add(new RegionCoord(
                Math.floorDiv(chunkX, REGION_CHUNKS),
                Math.floorDiv(chunkZ, REGION_CHUNKS)
        ));
    }

    private void markRegionDirty(int chunkX, int chunkZ) {
        RegionKey key = new RegionKey(
                Math.floorDiv(chunkX, REGION_CHUNKS),
                Math.floorDiv(chunkZ, REGION_CHUNKS)
        );
        RegionEntry region = regionEntries.get(key);
        if (region != null) region.dirty = true;
    }

    private void queueGenerate(ChunkKey key, boolean priority) {
        if (!queued.add(key)) return;
        if (priority) generateQueue.offerFirst(key);
        else generateQueue.offerLast(key);
    }

    private void generate(ClientLevel level, ChunkKey key) {
        // Work directly against the already-loaded client chunk. This avoids doing
        // a world/chunk lookup for every one of the 256 map columns.
        LevelChunk chunk;
        try {
            chunk = level.getChunk(key.x, key.z);
        } catch (Exception ex) {
            return;
        }

        int[] pixels = new int[TILE_BLOCKS * TILE_BLOCKS];
        int[] heights = new int[TILE_BLOCKS * TILE_BLOCKS];
        String[] ids = new String[TILE_BLOCKS * TILE_BLOCKS];
        int startX = key.x * 16;
        int startZ = key.z * 16;
        int minY = level.getMinY();

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int wx = startX + x;
                int wz = startZ + z;
                int reportedTop = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                int idx = z * 16 + x;

                if (reportedTop < minY) {
                    heights[idx] = minY;
                    ids[idx] = "minecraft:air";
                    continue;
                }

                // Some client-side heightmaps can lag a block behind recent builds.
                // Probe a few blocks ABOVE the reported surface first, then walk down
                // to the highest real non-air block. This makes the minimap favour the
                // actual visible roof/road/floor instead of the layer underneath it.
                int maxY = level.getMaxY() - 1;
                int top = Math.min(maxY, reportedTop + 4);
                BlockState state = chunk.getBlockState(new BlockPos(wx, top, wz));

                while (top > minY && state.isAir()) {
                    top--;
                    state = chunk.getBlockState(new BlockPos(wx, top, wz));
                }

                // Very thin vegetation overlays make a 1-pixel-per-block map look
                // noisy and over-saturated. Xaero-style maps read the ground shape
                // much more clearly. Keep real canopies (leaves) and snow, but look
                // through grass/flowers/petals to the supporting surface below.
                while (top > minY && isThinVegetation(state)) {
                    top--;
                    state = chunk.getBlockState(new BlockPos(wx, top, wz));
                }

                heights[idx] = top;
                ids[idx] = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            }
        }

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int idx = z * 16 + x;
                int h = heights[idx];
                int north = heights[Math.max(0, z - 1) * 16 + x];
                int south = heights[Math.min(15, z + 1) * 16 + x];
                int west = heights[z * 16 + Math.max(0, x - 1)];
                int east = heights[z * 16 + Math.min(15, x + 1)];
                int worldX = startX + x;
                int worldZ = startZ + z;
                pixels[idx] = BlockColorPalette.color(ids[idx], h, north, south, west, east, worldX, worldZ);
            }
        }

        int meaningful = 0;
        for (String id : ids) {
            if (id != null && !id.endsWith(":air")
                    && !id.contains(":cave_air") && !id.contains(":void_air")) meaningful++;
        }
        if (meaningful < 32) return;

        Entry entry = entries.computeIfAbsent(key, k -> new Entry());
        entry.generatedAt = System.currentTimeMillis();
        entry.readyPixels = pixels;
        entry.pixels = pixels;
        registerKnownChunk(key.x, key.z);
        markRegionDirty(key.x, key.z);
        writeAsync(key, pixels);
    }

    private static boolean isThinVegetation(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return id.contains("short_grass")
                || id.contains("tall_grass")
                || id.contains("fern")
                || id.contains("flower")
                || id.contains("tulip")
                || id.contains("dandelion")
                || id.contains("poppy")
                || id.contains("orchid")
                || id.contains("allium")
                || id.contains("azure_bluet")
                || id.contains("oxeye_daisy")
                || id.contains("cornflower")
                || id.contains("lily_of_the_valley")
                || id.contains("pink_petals")
                || id.contains("wildflowers")
                || id.contains("bush")
                || id.contains("dead_bush")
                || id.contains("torchflower")
                || id.contains("pitcher_plant");
    }

    private void indexDiskCacheAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(cacheRoot);
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(cacheRoot, "*.toa")) {
                    for (Path p : stream) {
                        String n = p.getFileName().toString();
                        if (!n.endsWith(".toa")) continue;
                        String stem = n.substring(0, n.length() - 4);
                        int split = stem.indexOf('_', 1);
                        if (split < 0) continue;
                        try {
                            int x = Integer.parseInt(stem.substring(0, split));
                            int z = Integer.parseInt(stem.substring(split + 1));
                            registerKnownChunk(x, z);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }, io);
    }

    private void writeAsync(ChunkKey key, int[] pixels) {
        int[] copy = pixels.clone();
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(cacheRoot);
                try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(fileFor(key)))) {
                    out.writeInt(CACHE_MAGIC);
                    for (int pixel : copy) out.writeInt(pixel);
                }
            } catch (Exception ignored) {}
        }, io);
    }

    private int[] readPixels(Path path) throws Exception {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            if (in.readInt() != CACHE_MAGIC) return null;
            int[] pixels = new int[TILE_BLOCKS * TILE_BLOCKS];
            for (int i = 0; i < pixels.length; i++) pixels[i] = in.readInt();
            return pixels;
        }
    }

    private Path fileFor(ChunkKey key) {
        return cacheRoot.resolve(key.x + "_" + key.z + ".toa");
    }

    private void upload(ChunkKey key, Entry entry, int[] pixels) {
        try {
            NativeImage nativeImage = new NativeImage(TILE_BLOCKS, TILE_BLOCKS, false);
            for (int y = 0; y < TILE_BLOCKS; y++) {
                for (int x = 0; x < TILE_BLOCKS; x++) {
                    nativeImage.setPixel(x, y, pixels[y * TILE_BLOCKS + x]);
                }
            }

            // IMPORTANT: use the exact chunk coordinates in the texture ID.
            // Objects.hash(x, z) is not unique (for example, neighbouring
            // coordinate pairs can collide), which allowed an unrelated map
            // chunk to overwrite this texture in Minecraft's texture manager.
            Identifier id = Identifier.fromNamespaceAndPath(
                    ToaMinimapClient.MOD_ID,
                    "client_map/x_" + key.x + "_z_" + key.z
            );

            if (entry.textureId != null) {
                try {
                    Minecraft.getInstance().getTextureManager().release(entry.textureId);
                } catch (Exception ignored) {
                    if (entry.texture != null) entry.texture.close();
                }
            }

            DynamicTexture texture = new DynamicTexture(() -> "TOA Minimap client map chunk", nativeImage);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            texture.upload();
            entry.texture = texture;
            entry.textureId = id;
        } catch (Exception ex) {
            System.err.println("[TOA Minimap] Failed to upload map chunk " + key + ": " + ex.getMessage());
        }
    }

    @Override
    public void close() {
        for (Entry e : entries.values()) {
            if (e.textureId != null) {
                try { Minecraft.getInstance().getTextureManager().release(e.textureId); }
                catch (Exception ignored) { if (e.texture != null) e.texture.close(); }
            }
        }
        for (RegionEntry region : regionEntries.values()) {
            if (region.textureId != null) {
                try { Minecraft.getInstance().getTextureManager().release(region.textureId); }
                catch (Exception ignored) { if (region.texture != null) region.texture.close(); }
            }
        }
        regionEntries.clear();
        entries.clear();
        io.shutdownNow();
    }

    public record TileTexture(Identifier id, int width, int height) {}
    public record RegionTexture(Identifier id, int width, int height) {}
    public record ChunkCoord(int x, int z) {}
    public record RegionCoord(int x, int z) {}
    private record ChunkKey(int x, int z) {}
    private record RegionKey(int x, int z) {}

    private static final class Entry {
        volatile int[] readyPixels;
        volatile int[] pixels;
        volatile Identifier textureId;
        volatile DynamicTexture texture;
        volatile boolean diskChecked;
        volatile boolean loadingDisk;
        volatile long generatedAt;
        volatile long lastAccess;
        volatile long lastRefreshQueuedAt;
    }

    private static final class RegionEntry {
        volatile int[] readyPixels;
        volatile Identifier textureId;
        volatile DynamicTexture texture;
        volatile boolean loading;
        volatile boolean dirty;
        volatile long lastAccess;
    }
}
