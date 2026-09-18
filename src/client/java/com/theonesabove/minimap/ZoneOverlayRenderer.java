package com.theonesabove.minimap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws server-authored TOAZones on the full World Map only.
 *
 * Security / discovery rule:
 * a zone is painted only inside map chunks already present in the player's
 * persistent ClientMapCache. Undiscovered chunks never reveal zone fill,
 * boundary extent or labels.
 */
public final class ZoneOverlayRenderer {
    private static final int SAFE_FILL = 0x2436B85B;
    private static final int SAFE_LINE = 0xE036B85B;

    private static final int PROPERTY_FILL = 0x243B82F6;
    private static final int PROPERTY_LINE = 0xE05AA0FF;

    private static final int TERRITORY_FILL = 0x24D95B43;
    private static final int TERRITORY_LINE = 0xE0E6785F;

    private static final int DEFAULT_FILL = 0x22D99718;
    private static final int DEFAULT_LINE = 0xE0E3B341;

    private final ClientMapCache cache;

    public ZoneOverlayRenderer(ClientMapCache cache) {
        this.cache = cache;
    }

    public void drawWorldMap(GuiGraphicsExtractor g, Minecraft mc, ServerAccessController access,
                             int left, int top, int width, int height,
                             double centerX, double centerZ, double blocksPerPixel) {
        if (mc.player == null || access.zoneMarkers().isEmpty() || blocksPerPixel <= 0.0) return;

        double minWorldX = centerX - width * blocksPerPixel * 0.5;
        double maxWorldX = centerX + width * blocksPerPixel * 0.5;
        double minWorldZ = centerZ - height * blocksPerPixel * 0.5;
        double maxWorldZ = centerZ + height * blocksPerPixel * 0.5;

        int minVisibleChunkX = floorChunk(minWorldX);
        int maxVisibleChunkX = floorChunk(maxWorldX);
        int minVisibleChunkZ = floorChunk(minWorldZ);
        int maxVisibleChunkZ = floorChunk(maxWorldZ);

        int screenCx = left + width / 2;
        int screenCy = top + height / 2;

        g.enableScissor(left, top, left + width, top + height);
        g.pose().pushMatrix();
        g.pose().translate(screenCx, screenCy);
        float scale = (float)(1.0 / blocksPerPixel);
        g.pose().scale(scale, scale);
        g.pose().translate((float)-centerX, (float)-centerZ);

        for (ZoneMarker zone : access.zoneMarkers()) {
            int zoneMinChunkX = Math.max(minVisibleChunkX, Math.floorDiv(zone.minX(), 16));
            int zoneMaxChunkX = Math.min(maxVisibleChunkX, Math.floorDiv(zone.maxX(), 16));
            int zoneMinChunkZ = Math.max(minVisibleChunkZ, Math.floorDiv(zone.minZ(), 16));
            int zoneMaxChunkZ = Math.min(maxVisibleChunkZ, Math.floorDiv(zone.maxZ(), 16));

            if (zoneMinChunkX > zoneMaxChunkX || zoneMinChunkZ > zoneMaxChunkZ) continue;

            int fill = fillColor(zone);
            int line = lineColor(zone);

            for (int chunkX = zoneMinChunkX; chunkX <= zoneMaxChunkX; chunkX++) {
                for (int chunkZ = zoneMinChunkZ; chunkZ <= zoneMaxChunkZ; chunkZ++) {
                    if (!cache.isKnownChunk(chunkX, chunkZ)) continue;

                    int chunkMinX = chunkX * 16;
                    int chunkMinZ = chunkZ * 16;
                    int chunkMaxX = chunkMinX + 16;
                    int chunkMaxZ = chunkMinZ + 16;

                    int x1 = Math.max(zone.minX(), chunkMinX);
                    int z1 = Math.max(zone.minZ(), chunkMinZ);
                    int x2 = Math.min(zone.maxX() + 1, chunkMaxX);
                    int z2 = Math.min(zone.maxZ() + 1, chunkMaxZ);
                    if (x2 <= x1 || z2 <= z1) continue;

                    // Fill only the discovered intersection of this zone.
                    g.fill(x1, z1, x2, z2, fill);

                    // Draw real zone borders only where the bordering chunk is discovered.
                    // Do not draw artificial borders around the edge of unexplored map data.
                    if (x1 == zone.minX()) g.fill(x1, z1, Math.min(x2, x1 + 1), z2, line);
                    if (x2 == zone.maxX() + 1) g.fill(Math.max(x1, x2 - 1), z1, x2, z2, line);
                    if (z1 == zone.minZ()) g.fill(x1, z1, x2, Math.min(z2, z1 + 1), line);
                    if (z2 == zone.maxZ() + 1) g.fill(x1, Math.max(z1, z2 - 1), x2, z2, line);
                }
            }
        }

        g.pose().popMatrix();
        g.disableScissor();

        drawDiscoveredLabels(g, mc, access, left, top, width, height, centerX, centerZ, blocksPerPixel);
    }

    private void drawDiscoveredLabels(GuiGraphicsExtractor g, Minecraft mc, ServerAccessController access,
                                      int left, int top, int width, int height,
                                      double centerX, double centerZ, double blocksPerPixel) {
        int screenCx = left + width / 2;
        int screenCy = top + height / 2;

        for (ZoneMarker zone : access.zoneMarkers()) {
            int centerChunkX = floorChunk(zone.centerX());
            int centerChunkZ = floorChunk(zone.centerZ());

            // The name itself is information, so only reveal it after the player
            // has discovered the chunk containing the zone's centre.
            if (!cache.isKnownChunk(centerChunkX, centerChunkZ)) continue;

            double sx = screenCx + (zone.centerX() - centerX) / blocksPerPixel;
            double sy = screenCy + (zone.centerZ() - centerZ) / blocksPerPixel;

            if (sx < left + 10 || sx > left + width - 10
                    || sy < top + 10 || sy > top + height - 10) continue;

            String label = zone.name();
            if (label == null || label.isBlank()) continue;

            int color = labelColor(zone);
            float textScale = blocksPerPixel >= 8.0 ? 0.52f : 0.62f;
            int textWidth = mc.font.width(label);

            // Clean floating label: no opaque background box.
            // A subtle Minecraft text shadow keeps it readable over terrain.
            g.pose().pushMatrix();
            g.pose().translate((float)sx, (float)sy);
            g.pose().scale(textScale, textScale);
            g.text(mc.font, label, -textWidth / 2, -4, color, true);
            g.pose().popMatrix();
        }
    }

    private static int fillColor(ZoneMarker zone) {
        String kind = kind(zone);
        return switch (kind) {
            case "safe" -> SAFE_FILL;
            case "property" -> PROPERTY_FILL;
            case "territory" -> TERRITORY_FILL;
            default -> DEFAULT_FILL;
        };
    }

    private static int lineColor(ZoneMarker zone) {
        String kind = kind(zone);
        return switch (kind) {
            case "safe" -> SAFE_LINE;
            case "property" -> PROPERTY_LINE;
            case "territory" -> TERRITORY_LINE;
            default -> DEFAULT_LINE;
        };
    }

    private static int labelColor(ZoneMarker zone) {
        String kind = kind(zone);
        return switch (kind) {
            case "safe" -> 0xFFD7FFE1;
            case "property" -> 0xFFD8E8FF;
            case "territory" -> 0xFFFFD8D1;
            default -> 0xFFFFE8B2;
        };
    }

    private static String kind(ZoneMarker zone) {
        String text = ((zone.id() == null ? "" : zone.id()) + " "
                + (zone.name() == null ? "" : zone.name()) + " "
                + (zone.preset() == null ? "" : zone.preset())).toLowerCase();

        if (zone.safeZone() || text.contains("spawn")) return "safe";
        if (text.contains("property") || text.contains("plot") || text.contains("estate")) return "property";
        if (text.contains("territory") || text.contains("faction") || text.contains("claim")) return "territory";
        return "zone";
    }

    private static int floorChunk(double block) {
        return (int)Math.floor(block / 16.0);
    }
}
