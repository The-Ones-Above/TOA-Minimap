package com.theonesabove.minimap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MinimapHud {
    private static final int OTHER_PLAYER_COLOR = 0xFFFFFFFF;
    private static final int NPC_COLOR = 0xFFFFD21F;

    private final MinimapConfig config;
    private final MapViewRenderer renderer;
    private final ServerAccessController access;
    private boolean editing;
    private Bounds lastBounds = new Bounds(0,0,0,0);

    public MinimapHud(MinimapConfig config, MapViewRenderer renderer, ServerAccessController access) {
        this.config = config;
        this.renderer = renderer;
        this.access = access;
    }

    public void setEditing(boolean editing) { this.editing = editing; }
    public Bounds bounds() { return lastBounds; }

    public void render(GuiGraphicsExtractor g) { if (!editing) renderInternal(g); }
    public void renderEditor(GuiGraphicsExtractor g) { renderInternal(g); }

    private void renderInternal(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (!config.enabled || mc.player == null || mc.level == null || !access.hasCompass()) return;
        int size = config.minimapSize;
        int right = g.guiWidth() - config.margin + config.offsetX;
        int top = config.margin + config.offsetY;
        right = Math.max(size + 2, Math.min(g.guiWidth() - 1, right));
        top = Math.max(1, Math.min(g.guiHeight() - size - 12, top));
        int left = right - size;
        lastBounds = new Bounds(left, top, size, size);

        g.fill(left - 1, top - 1, right + 1, top + size + 1, 0xB9000000);
        g.fill(left, top, right, top + size, 0xFF080808);

        double heading = normalize(mc.player.getYRot() + 180.0);
        double rotation = config.rotateWithPlayer ? -heading : 0.0;
        renderer.draw(g, mc, left, top, size, size,
                mc.player.getX(), mc.player.getZ(),
                config.minimapBlocksPerPixel,
                rotation);

        drawEntityMarkers(g, mc, left, top, size, rotation);

        int cx = left + size/2, cy = top + size/2;
        // Local player marker: white centre with a thin black outline.
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFF000000);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);

        if (config.showCoordinates) {
            String s = String.format("%d  %d  %d", (int)Math.floor(mc.player.getX()), (int)Math.floor(mc.player.getY()), (int)Math.floor(mc.player.getZ()));
            float scale = 0.52f;
            int w = mc.font.width(s);
            g.pose().pushMatrix();
            g.pose().translate(left + size/2, top + size + 2);
            g.pose().scale(scale, scale);
            g.text(mc.font, s, -w/2, 0, 0xFF7F7F7F, false);
            g.pose().popMatrix();
        }
    }

    private void drawEntityMarkers(GuiGraphicsExtractor g, Minecraft mc,
                                   int left, int top, int size, double rotationDegrees) {
        int cx = left + size / 2;
        int cy = top + size / 2;
        Set<UUID> npcIds = new HashSet<>();

        for (NpcMarker npc : access.npcMarkers()) {
            npcIds.add(npc.uuid());
            drawWorldDot(g, mc, left, top, size, cx, cy,
                    npc.x(), npc.z(), rotationDegrees, NPC_COLOR);
        }

        // Real remote players are rendered from live client entities for smooth movement.
        // Citizens player NPC UUIDs supplied by the server are excluded from this white-dot pass.
        for (var player : mc.level.players()) {
            if (player == mc.player || npcIds.contains(player.getUUID())) continue;
            drawWorldDot(g, mc, left, top, size, cx, cy,
                    player.getX(), player.getZ(), rotationDegrees, OTHER_PLAYER_COLOR);
        }
    }

    private void drawWorldDot(GuiGraphicsExtractor g, Minecraft mc,
                              int left, int top, int size, int cx, int cy,
                              double worldX, double worldZ, double rotationDegrees, int color) {
        double dx = worldX - mc.player.getX();
        double dz = worldZ - mc.player.getZ();
        double radians = Math.toRadians(rotationDegrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double rx = dx * cos - dz * sin;
        double rz = dx * sin + dz * cos;

        int x = (int)Math.round(cx + rx / config.minimapBlocksPerPixel);
        int y = (int)Math.round(cy + rz / config.minimapBlocksPerPixel);

        // Keep dots entirely inside the minimap and never leak into the border/coordinates.
        if (x < left + 2 || x >= left + size - 2 || y < top + 2 || y >= top + size - 2) return;
        g.fill(x - 1, y - 1, x + 2, y + 2, 0xD9000000);
        g.fill(x, y, x + 1, y + 1, color);
    }

    private static double normalize(double d) { d %= 360.0; return d < 0 ? d + 360.0 : d; }

    public record Bounds(int x, int y, int width, int height) {
        public boolean contains(double mx, double my) { return mx >= x && mx < x+width && my >= y && my < y+height; }
    }
}
