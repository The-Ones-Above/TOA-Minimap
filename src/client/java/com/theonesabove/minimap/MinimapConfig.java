package com.theonesabove.minimap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MinimapConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("toa-minimap.json");
    private static final int CURRENT_CONFIG_VERSION = 14;

    public int configVersion = CURRENT_CONFIG_VERSION;
    public int minimapSize = 78;
    public int margin = 7;
    public int offsetX = 0;
    public int offsetY = 0;

    // Minimap remains at an exact crisp level; World Map now zooms continuously.
    public double minimapBlocksPerPixel = 1.75;
    public double worldMapDefaultBlocksPerPixel = 2.0;
    public int worldMapMaxBlocksPerPixel = 16;
    public int prefetchChunkRadius = 12;

    public boolean enabled = true;
    public boolean showCoordinates = true;
    public boolean rotateWithPlayer = true;

    public static MinimapConfig load() {
        MinimapConfig config = null;
        if (Files.exists(PATH)) {
            try { config = GSON.fromJson(Files.readString(PATH), MinimapConfig.class); }
            catch (Exception ignored) {}
        }
        if (config == null) config = new MinimapConfig();

        if (config.configVersion < CURRENT_CONFIG_VERSION) {
            config.configVersion = CURRENT_CONFIG_VERSION;
            config.minimapSize = Math.max(config.minimapSize, 78);
            // v2.4.2: slightly closer HUD view and a closer default World Map view.
            // World Map can still be zoomed back out up to worldMapMaxBlocksPerPixel.
            config.minimapBlocksPerPixel = 1.75;
            config.worldMapDefaultBlocksPerPixel = 2.0;
            config.worldMapMaxBlocksPerPixel = 16;
            config.prefetchChunkRadius = 12;
        }

        config.sanitize();
        config.save();
        return config;
    }

    public void sanitize() {
        minimapSize = clamp(minimapSize, 44, 180);
        margin = clamp(margin, 0, 64);
        offsetX = clamp(offsetX, -2000, 2000);
        offsetY = clamp(offsetY, -2000, 2000);
        minimapBlocksPerPixel = clamp(minimapBlocksPerPixel, 0.75, 4.0);
        worldMapDefaultBlocksPerPixel = clamp(worldMapDefaultBlocksPerPixel, 0.5, 8.0);
        worldMapMaxBlocksPerPixel = snapIntegerZoom(worldMapMaxBlocksPerPixel,
                (int)Math.ceil(worldMapDefaultBlocksPerPixel), 32);
        prefetchChunkRadius = clamp(prefetchChunkRadius, 4, 24);
    }

    public void moveTransient(int dx, int dy) {
        offsetX = clamp(offsetX + dx, -2000, 2000);
        offsetY = clamp(offsetY + dy, -2000, 2000);
    }

    public void resizeTransient(int delta) {
        minimapSize = clamp(minimapSize + delta, 44, 180);
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException ignored) {}
    }

    private static int snapIntegerZoom(int value, int min, int max) {
        int[] levels = {1, 2, 4, 8, 16, 32};
        int best = Math.max(min, Math.min(max, value));
        int distance = Integer.MAX_VALUE;
        for (int level : levels) {
            if (level < min || level > max) continue;
            int d = Math.abs(level - value);
            if (d < distance) {
                distance = d;
                best = level;
            }
        }
        return best;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
