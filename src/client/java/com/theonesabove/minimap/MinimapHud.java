package com.theonesabove.minimap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class MinimapHud {
    private final MinimapConfig config;
    private final MapViewRenderer renderer;
    private boolean editing;
    private Bounds lastBounds = new Bounds(0,0,0,0);

    public MinimapHud(MinimapConfig config, MapViewRenderer renderer) {
        this.config = config;
        this.renderer = renderer;
    }

    public void setEditing(boolean editing) { this.editing = editing; }
    public Bounds bounds() { return lastBounds; }

    public void render(GuiGraphicsExtractor g) { if (!editing) renderInternal(g); }
    public void renderEditor(GuiGraphicsExtractor g) { renderInternal(g); }

    private void renderInternal(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (!config.enabled || mc.player == null || mc.level == null) return;
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
        renderer.draw(g, mc, left, top, size, size,
                mc.player.getX(), mc.player.getZ(),
                config.minimapBlocksPerPixel,
                config.rotateWithPlayer ? -heading : 0.0);

        int cx = left + size/2, cy = top + size/2;
        // Compact player marker: white centre with a one-pixel black outline.
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

    private static double normalize(double d) { d %= 360.0; return d < 0 ? d + 360.0 : d; }

    public record Bounds(int x, int y, int width, int height) {
        public boolean contains(double mx, double my) { return mx >= x && mx < x+width && my >= y && my < y+height; }
    }
}
