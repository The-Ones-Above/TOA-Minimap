package com.theonesabove.minimap;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Full-screen map with a continuous camera, smooth zoom and persistent cached terrain. */
public final class WorldMapScreen extends Screen {
    private static final double MIN_BPP = 0.5;
    private static final double ZOOM_IN_FACTOR = 0.80;
    private static final double ZOOM_OUT_FACTOR = 1.25;
    private static final double ZOOM_LERP = 0.24;

    private final MinimapConfig config;
    private final MapViewRenderer renderer;
    private final KeyMapping worldMapKey;
    private final ServerAccessController access;
    private final ZoneOverlayRenderer zoneOverlay;
    private final FactionOverlayRenderer factionOverlay;
    private double centerX, centerZ;
    private double currentBpp;
    private double targetBpp;
    private boolean dragging;
    private double lastX, lastY;

    private boolean zoomAnchored;
    private double anchorScreenX, anchorScreenY;
    private double anchorWorldX, anchorWorldZ;

    public WorldMapScreen(MinimapConfig config, MapViewRenderer renderer,
                          ClientMapCache cache, ServerAccessController access,
                          KeyMapping worldMapKey) {
        super(Component.literal("TOA World Map"));
        this.config = config;
        this.renderer = renderer;
        this.worldMapKey = worldMapKey;
        this.access = access;
        this.zoneOverlay = new ZoneOverlayRenderer(cache);
        this.factionOverlay = new FactionOverlayRenderer(cache);
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) {
            centerX = player.getX();
            centerZ = player.getZ();
        }
        currentBpp = config.worldMapDefaultBlocksPerPixel;
        targetBpp = currentBpp;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        updateSmoothZoom();

        g.fill(0, 0, width, height, 0xF1000000);
        int pad = 18;
        int left = pad, top = pad, right = width - pad, bottom = height - pad;
        g.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF303030);
        g.fill(left, top, right, bottom, 0xFF101010);

        renderer.drawWorldMap(g, minecraft, left, top, right - left, bottom - top,
                centerX, centerZ, currentBpp);

        // Server-authored zones are a World Map feature only. The overlay is
        // clipped to chunks this client has already discovered/cached.
        zoneOverlay.drawWorldMap(g, minecraft, access,
                left, top, right - left, bottom - top,
                centerX, centerZ, currentBpp);

        factionOverlay.draw(g, minecraft, access,
                left, top, right - left, bottom - top,
                centerX, centerZ, currentBpp, mouseX, mouseY);

        if (minecraft.player != null) {
            int px = (int)Math.round(width / 2.0 + (minecraft.player.getX() - centerX) / currentBpp);
            int py = (int)Math.round(height / 2.0 + (minecraft.player.getZ() - centerZ) / currentBpp);
            if (px >= left && px < right && py >= top && py < bottom) {
                // Small white player dot with a one-pixel black outline for contrast.
                g.fill(px - 2, py - 2, px + 2, py + 2, 0xFF000000);
                g.fill(px - 1, py - 1, px + 1, py + 1, 0xFFFFFFFF);
            }
        }

        String footer = "Drag to pan  •  Scroll to zoom  •  X / Esc to close";
        g.text(font, footer, (width - font.width(footer)) / 2, height - 14, 0xFF777777, false);
    }

    private void updateSmoothZoom() {
        double diff = targetBpp - currentBpp;
        if (Math.abs(diff) < 0.0005) {
            currentBpp = targetBpp;
            if (zoomAnchored) recenterForAnchor();
            return;
        }
        currentBpp += diff * ZOOM_LERP;
        if (zoomAnchored) recenterForAnchor();
    }

    private void recenterForAnchor() {
        double screenCx = width / 2.0;
        double screenCy = height / 2.0;
        centerX = anchorWorldX - (anchorScreenX - screenCx) * currentBpp;
        centerZ = anchorWorldZ - (anchorScreenY - screenCy) * currentBpp;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = true;
            zoomAnchored = false;
            targetBpp = currentBpp;
            lastX = e.x();
            lastY = e.y();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (!dragging || e.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        centerX -= (e.x() - lastX) * currentBpp;
        centerZ -= (e.y() - lastY) * currentBpp;
        lastX = e.x();
        lastY = e.y();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (dy == 0) return false;

        // Anchor zoom to the exact world point under the cursor. This remains
        // stable throughout the interpolation, which prevents the map jumping.
        double screenCx = width / 2.0;
        double screenCy = height / 2.0;
        anchorScreenX = mx;
        anchorScreenY = my;
        anchorWorldX = centerX + (mx - screenCx) * currentBpp;
        anchorWorldZ = centerZ + (my - screenCy) * currentBpp;
        zoomAnchored = true;

        double factor = dy > 0 ? ZOOM_IN_FACTOR : ZOOM_OUT_FACTOR;
        targetBpp = clamp(targetBpp * factor, MIN_BPP, config.worldMapMaxBlocksPerPixel);
        return true;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (worldMapKey != null && worldMapKey.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
}
