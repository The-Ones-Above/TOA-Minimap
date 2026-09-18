package com.theonesabove.minimap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Draws cached 16x16 map tiles through one continuous camera transform.
 * Tiles always remain in world-block coordinates; zoom is applied to the whole
 * map in one transform, so adjacent chunks cannot overlap or separate while zooming.
 */
public final class MapViewRenderer {
    private static final long SPARSE_THRESHOLD = 4096L;
    private final ClientMapCache cache;

    public MapViewRenderer(ClientMapCache cache) {
        this.cache = cache;
    }

    public void draw(GuiGraphicsExtractor graphics, Minecraft mc,
                     int left, int top, int width, int height,
                     double centerBlockX, double centerBlockZ,
                     double blocksPerPixel, double rotationDegrees) {
        if (mc.level == null || blocksPerPixel <= 0.0) return;

        int cx = left + width / 2;
        int cy = top + height / 2;
        double halfBlocksX = width * blocksPerPixel * 0.5 + 32.0;
        double halfBlocksZ = height * blocksPerPixel * 0.5 + 32.0;
        double radius = Math.hypot(halfBlocksX, halfBlocksZ);

        int minChunkX = floorDiv(centerBlockX - radius, 16);
        int maxChunkX = floorDiv(centerBlockX + radius, 16);
        int minChunkZ = floorDiv(centerBlockZ - radius, 16);
        int maxChunkZ = floorDiv(centerBlockZ + radius, 16);

        graphics.enableScissor(left, top, left + width, top + height);
        graphics.pose().pushMatrix();

        // One camera transform for the entire map. This is the key to seamless,
        // fluid zooming: individual chunk textures are never resized independently.
        graphics.pose().translate(cx, cy);
        if (Math.abs(rotationDegrees) > 0.001) {
            graphics.pose().rotate((float)Math.toRadians(rotationDegrees));
        }
        float scale = (float)(1.0 / blocksPerPixel);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)-centerBlockX, (float)-centerBlockZ);

        long candidateCount = (long)(maxChunkX - minChunkX + 1)
                * (long)(maxChunkZ - minChunkZ + 1);

        if (candidateCount > SPARSE_THRESHOLD) {
            for (ClientMapCache.ChunkCoord coord : cache.knownChunks()) {
                if (coord.x() < minChunkX || coord.x() > maxChunkX
                        || coord.z() < minChunkZ || coord.z() > maxChunkZ) continue;
                drawChunk(graphics, mc, coord.x(), coord.z());
            }
        } else {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    drawChunk(graphics, mc, chunkX, chunkZ);
                }
            }
        }

        graphics.pose().popMatrix();
        graphics.disableScissor();
    }


    /**
     * High-performance World Map path. Uses 256x256 block region atlases rather
     * than one texture draw per Minecraft chunk.
     */
    public void drawWorldMap(GuiGraphicsExtractor graphics, Minecraft mc,
                             int left, int top, int width, int height,
                             double centerBlockX, double centerBlockZ,
                             double blocksPerPixel) {
        if (mc.level == null || blocksPerPixel <= 0.0) return;

        int cx = left + width / 2;
        int cy = top + height / 2;
        double halfBlocksX = width * blocksPerPixel * 0.5 + ClientMapCache.REGION_BLOCKS;
        double halfBlocksZ = height * blocksPerPixel * 0.5 + ClientMapCache.REGION_BLOCKS;

        int minRegionX = floorDiv(centerBlockX - halfBlocksX, ClientMapCache.REGION_BLOCKS);
        int maxRegionX = floorDiv(centerBlockX + halfBlocksX, ClientMapCache.REGION_BLOCKS);
        int minRegionZ = floorDiv(centerBlockZ - halfBlocksZ, ClientMapCache.REGION_BLOCKS);
        int maxRegionZ = floorDiv(centerBlockZ + halfBlocksZ, ClientMapCache.REGION_BLOCKS);

        graphics.enableScissor(left, top, left + width, top + height);
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        float scale = (float)(1.0 / blocksPerPixel);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)-centerBlockX, (float)-centerBlockZ);

        for (ClientMapCache.RegionCoord coord : cache.knownRegions()) {
            if (coord.x() < minRegionX || coord.x() > maxRegionX
                    || coord.z() < minRegionZ || coord.z() > maxRegionZ) continue;

            ClientMapCache.RegionTexture texture = cache.regionTexture(mc, coord.x(), coord.z());
            if (texture == null) continue;

            int worldX = coord.x() * ClientMapCache.REGION_BLOCKS;
            int worldZ = coord.z() * ClientMapCache.REGION_BLOCKS;
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    texture.id(),
                    worldX, worldZ,
                    0, 0,
                    ClientMapCache.REGION_BLOCKS, ClientMapCache.REGION_BLOCKS,
                    texture.width(), texture.height()
            );
        }

        graphics.pose().popMatrix();
        graphics.disableScissor();
    }

    private void drawChunk(GuiGraphicsExtractor graphics, Minecraft mc, int chunkX, int chunkZ) {
        ClientMapCache.TileTexture texture = cache.texture(mc, chunkX, chunkZ);
        if (texture == null) return;

        int worldX = chunkX * ClientMapCache.TILE_BLOCKS;
        int worldZ = chunkZ * ClientMapCache.TILE_BLOCKS;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture.id(),
                worldX, worldZ,
                0, 0,
                ClientMapCache.TILE_BLOCKS, ClientMapCache.TILE_BLOCKS,
                texture.width(), texture.height()
        );
    }

    private static int floorDiv(double value, int divisor) {
        return (int)Math.floor(value / divisor);
    }
}
