package com.theonesabove.minimap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;


public final class MinimapHud {
    private static final int OTHER_PLAYER_COLOR = 0xFFFFFFFF;
    private static final int NPC_COLOR = 0xFFFFD21F;

    private final MinimapConfig config;
    private final MapViewRenderer renderer;
    private final ServerAccessController access;
    private final MarkerTracker markerTracker = new MarkerTracker();
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
        drawCompass(g, mc, left, top, size, heading);

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
        MarkerTracker.Frame frame = markerTracker.update(mc, access);

        // Citizens / TOAShops NPCs: solid yellow, no outline.
        for (MarkerTracker.SmoothMarker npc : frame.npcs().values()) {
            drawWorldDot(g, mc, left, top, size, cx, cy,
                    npc.x(), npc.z(), rotationDegrees, NPC_COLOR, true);
        }

        // Other real players: solid white, no outline.
        for (MarkerTracker.SmoothMarker player : frame.players().values()) {
            drawWorldDot(g, mc, left, top, size, cx, cy,
                    player.x(), player.z(), rotationDegrees, OTHER_PLAYER_COLOR, false);
        }
    }

    private void drawWorldDot(GuiGraphicsExtractor g, Minecraft mc,
                              int left, int top, int size, int cx, int cy,
                              double worldX, double worldZ, double rotationDegrees, int color, boolean npc) {
        double dx = worldX - mc.player.getX();
        double dz = worldZ - mc.player.getZ();
        double radians = Math.toRadians(rotationDegrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double rx = dx * cos - dz * sin;
        double rz = dx * sin + dz * cos;

        // Keep screen coordinates as floating point so markers can move by fractions of a GUI pixel.
        double x = cx + rx / config.minimapBlocksPerPixel;
        double y = cy + rz / config.minimapBlocksPerPixel;

        if (x < left + 2.0 || x >= left + size - 2.0 || y < top + 2.0 || y >= top + size - 2.0) return;

        g.pose().pushMatrix();
        g.pose().translate((float)x, (float)y);

        // NPCs and other players deliberately use the same compact marker size.
        // Black outer square = thin outline, inner square = marker colour.
        // NPCs are yellow; real players are white.
        g.fill(-1, -1, 2, 2, 0xFF000000);
        g.fill(0, 0, 1, 1, color);

        g.pose().popMatrix();
    }


    /**
     * Xaero-style lightweight edge compass. It is calculated directly from the
     * local player's current yaw every render frame, so there is no network
     * update interval or smoothing delay.
     */
    private void drawCompass(GuiGraphicsExtractor g, Minecraft mc,
                             int left, int top, int size, double headingDegrees) {
        int cx = left + size / 2;
        int cy = top + size / 2;
        double inset = Math.max(4.0, size * 0.055);
        double half = size * 0.5 - inset;

        String[] labels = {"N", "E", "S", "W"};
        double[] bearings = {0.0, 90.0, 180.0, 270.0};

        for (int i = 0; i < labels.length; i++) {
            double relative = Math.toRadians(bearings[i] - headingDegrees);
            double vx = Math.sin(relative);
            double vy = -Math.cos(relative);
            double denom = Math.max(Math.abs(vx), Math.abs(vy));
            if (denom < 0.0001) continue;

            double t = half / denom;
            double x = cx + vx * t;
            double y = cy + vy * t;

            String label = labels[i];
            float scale = 0.44f;
            int w = mc.font.width(label);

            g.pose().pushMatrix();
            g.pose().translate((float)x, (float)y);
            g.pose().scale(scale, scale);
            g.text(mc.font, label, -w / 2, -4, 0xFFE8E8E8, true);
            g.pose().popMatrix();
        }
    }

    private static double normalize(double d) { d %= 360.0; return d < 0 ? d + 360.0 : d; }

    public record Bounds(int x, int y, int width, int height) {
        public boolean contains(double mx, double my) { return mx >= x && mx < x+width && my >= y && my < y+height; }
    }
}
