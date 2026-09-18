package com.theonesabove.minimap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Full World Map faction overlays.
 *
 * Every TOAFactions-owned land type intentionally shares the same colour.
 * Territory/property/HQ regions do not print faction names on the map.
 * Hovering a discovered faction area reveals its owner.
 * Turfs may display their own turf name.
 */
public final class FactionOverlayRenderer {
    private static final int UNCLAIMED_FILL = 0x244F5660;
    private static final int UNCLAIMED_LINE = 0xD08A929E;
    private static final int TURF_LABEL = 0xFFF1F1F1;

    private final ClientMapCache cache;

    public FactionOverlayRenderer(ClientMapCache cache) {
        this.cache = cache;
    }

    public void draw(GuiGraphicsExtractor g, Minecraft mc, ServerAccessController access,
                     int left, int top, int width, int height,
                     double centerX, double centerZ, double blocksPerPixel,
                     int mouseX, int mouseY) {
        if (mc.player == null || access.factionAreas().isEmpty() || blocksPerPixel <= 0.0) return;

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

        for (FactionAreaMarker area : access.factionAreas()) {
            drawDiscoveredArea(g, area, minVisibleChunkX, maxVisibleChunkX,
                    minVisibleChunkZ, maxVisibleChunkZ);
        }

        g.pose().popMatrix();
        g.disableScissor();

        drawTurfLabels(g, mc, access, left, top, width, height,
                centerX, centerZ, blocksPerPixel);
        drawHover(g, mc, access, left, top, width, height,
                centerX, centerZ, blocksPerPixel, mouseX, mouseY);
    }

    private void drawDiscoveredArea(GuiGraphicsExtractor g, FactionAreaMarker area,
                                    int minVisibleChunkX, int maxVisibleChunkX,
                                    int minVisibleChunkZ, int maxVisibleChunkZ) {
        int minChunkX = Math.max(minVisibleChunkX, Math.floorDiv(area.minX(), 16));
        int maxChunkX = Math.min(maxVisibleChunkX, Math.floorDiv(area.maxX(), 16));
        int minChunkZ = Math.max(minVisibleChunkZ, Math.floorDiv(area.minZ(), 16));
        int maxChunkZ = Math.min(maxVisibleChunkZ, Math.floorDiv(area.maxZ(), 16));

        if (minChunkX > maxChunkX || minChunkZ > maxChunkZ) return;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!cache.isKnownChunk(chunkX, chunkZ)) continue;

                int chunkMinX = chunkX * 16;
                int chunkMinZ = chunkZ * 16;
                int chunkMaxX = chunkMinX + 16;
                int chunkMaxZ = chunkMinZ + 16;

                int x1 = Math.max(area.minX(), chunkMinX);
                int z1 = Math.max(area.minZ(), chunkMinZ);
                int x2 = Math.min(area.maxX() + 1, chunkMaxX);
                int z2 = Math.min(area.maxZ() + 1, chunkMaxZ);
                if (x2 <= x1 || z2 <= z1) continue;

                int rgb = factionRgb(area.color());
                int fill = area.owner() == null || area.owner().isBlank() || "Unclaimed".equalsIgnoreCase(area.owner())
                        ? UNCLAIMED_FILL : withAlpha(rgb, 0x28);
                int line = area.owner() == null || area.owner().isBlank() || "Unclaimed".equalsIgnoreCase(area.owner())
                        ? UNCLAIMED_LINE : withAlpha(rgb, 0xE0);

                g.fill(x1, z1, x2, z2, fill);

                // Only real area edges are outlined. Discovery boundaries do not
                // create fake outlines that reveal unexplored geometry.
                if (x1 == area.minX()) g.fill(x1, z1, Math.min(x2, x1 + 1), z2, line);
                if (x2 == area.maxX() + 1) g.fill(Math.max(x1, x2 - 1), z1, x2, z2, line);
                if (z1 == area.minZ()) g.fill(x1, z1, x2, Math.min(z2, z1 + 1), line);
                if (z2 == area.maxZ() + 1) g.fill(x1, Math.max(z1, z2 - 1), x2, z2, line);
            }
        }
    }

    private void drawTurfLabels(GuiGraphicsExtractor g, Minecraft mc, ServerAccessController access,
                                int left, int top, int width, int height,
                                double centerX, double centerZ, double blocksPerPixel) {
        int screenCx = left + width / 2;
        int screenCy = top + height / 2;

        for (FactionAreaMarker area : access.factionAreas()) {
            if (!area.turf() || area.label() == null || area.label().isBlank()) continue;
            if (!cache.isKnownChunk(floorChunk(area.centerX()), floorChunk(area.centerZ()))) continue;

            double sx = screenCx + (area.centerX() - centerX) / blocksPerPixel;
            double sy = screenCy + (area.centerZ() - centerZ) / blocksPerPixel;
            if (sx < left + 12 || sx > left + width - 12
                    || sy < top + 10 || sy > top + height - 10) continue;

            drawLabel(g, mc, area.label(), sx, sy, TURF_LABEL);
        }
    }

    private void drawHover(GuiGraphicsExtractor g, Minecraft mc, ServerAccessController access,
                           int left, int top, int width, int height,
                           double centerX, double centerZ, double blocksPerPixel,
                           int mouseX, int mouseY) {
        if (mouseX < left || mouseX >= left + width || mouseY < top || mouseY >= top + height) return;

        double worldX = centerX + (mouseX - (left + width / 2.0)) * blocksPerPixel;
        double worldZ = centerZ + (mouseY - (top + height / 2.0)) * blocksPerPixel;
        if (!cache.isKnownChunk(floorChunk(worldX), floorChunk(worldZ))) return;

        FactionAreaMarker hovered = null;
        // Prefer turfs when areas overlap because their name is meaningful.
        for (FactionAreaMarker area : access.factionAreas()) {
            if (!area.contains(worldX, worldZ)) continue;
            if (hovered == null || area.turf()) hovered = area;
            if (area.turf()) break;
        }
        if (hovered == null) return;

        String owner = hovered.owner() == null || hovered.owner().isBlank()
                ? "Unclaimed" : hovered.owner();
        String line1 = hovered.turf() && hovered.label() != null && !hovered.label().isBlank()
                ? hovered.label()
                : prettyType(hovered.kind());

        int ownerColor = "Unclaimed".equalsIgnoreCase(owner)
                ? 0xFFAAAAAA
                : 0xFF000000 | factionRgb(hovered.color());

        drawTooltip(g, mc, mouseX + 10, mouseY + 10, line1, owner, ownerColor);
    }

    private static void drawLabel(GuiGraphicsExtractor g, Minecraft mc,
                                  String text, double x, double y, int color) {
        float scale = 0.62f;
        int w = mc.font.width(text);

        // Turf names use the same clean floating-label style as normal zones.
        // Keep only a subtle text shadow; no permanent black rectangle.
        g.pose().pushMatrix();
        g.pose().translate((float)x, (float)y);
        g.pose().scale(scale, scale);
        g.text(mc.font, text, -w / 2, -4, color, true);
        g.pose().popMatrix();
    }

    private static void drawTooltip(GuiGraphicsExtractor g, Minecraft mc,
                                    int x, int y, String line1,
                                    String owner, int ownerColor) {
        String prefix = "Owned by ";
        int prefixWidth = mc.font.width(prefix);
        int ownerWidth = mc.font.width(owner);
        int line2Width = prefixWidth + ownerWidth;

        int w = Math.max(mc.font.width(line1), line2Width) + 10;
        int h = 24;
        int maxX = g.guiWidth() - w - 4;
        int maxY = g.guiHeight() - h - 4;
        x = Math.max(4, Math.min(maxX, x));
        y = Math.max(4, Math.min(maxY, y));

        g.fill(x, y, x + w, y + h, 0xE0101010);
        g.fill(x, y, x + w, y + 1, 0xD0A0A0A0);

        g.text(mc.font, line1, x + 5, y + 4, 0xFFFFFFFF, false);

        // Keep the descriptor neutral, but colour the faction name with its
        // actual TOAFactions configured colour.
        int textX = x + 5;
        int textY = y + 14;
        g.text(mc.font, prefix, textX, textY, 0xFFBFC7D5, false);
        g.text(mc.font, owner, textX + prefixWidth, textY, ownerColor, false);
    }

    private static String prettyType(String kind) {
        if (kind == null) return "Faction Land";
        return switch (kind.toLowerCase()) {
            case "property" -> "Faction Property";
            case "hq" -> "Faction HQ";
            case "territory" -> "Faction Territory";
            default -> "Faction Land";
        };
    }


    /**
     * TOAFactions stores colours using Bukkit ChatColor names (for example
     * RED, DARK_RED, AQUA, BLUE). Convert those names to RGB for map overlays.
     */
    private static int factionRgb(String colorName) {
        if (colorName == null || colorName.isBlank()) return 0xFFFFFF;

        return switch (colorName.toUpperCase(java.util.Locale.ROOT)) {
            case "BLACK" -> 0x2A2A2A;
            case "DARK_BLUE" -> 0x0000AA;
            case "DARK_GREEN" -> 0x00AA00;
            case "DARK_AQUA" -> 0x00AAAA;
            case "DARK_RED" -> 0xAA0000;
            case "DARK_PURPLE" -> 0xAA00AA;
            case "GOLD" -> 0xFFAA00;
            case "GRAY", "GREY" -> 0xAAAAAA;
            case "DARK_GRAY", "DARK_GREY" -> 0x555555;
            case "BLUE" -> 0x5555FF;
            case "GREEN" -> 0x55FF55;
            case "AQUA" -> 0x55FFFF;
            case "RED" -> 0xFF5555;
            case "LIGHT_PURPLE" -> 0xFF55FF;
            case "YELLOW" -> 0xFFFF55;
            case "WHITE" -> 0xFFFFFF;
            default -> 0xFFFFFF;
        };
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }

    private static int floorChunk(double block) {
        return (int)Math.floor(block / 16.0);
    }
}
