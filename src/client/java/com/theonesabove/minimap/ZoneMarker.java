package com.theonesabove.minimap;

/** Server-authored rectangular TOAZones region displayed on the HUD minimap only. */
public record ZoneMarker(
        String id,
        String name,
        int minX,
        int minZ,
        int maxX,
        int maxZ,
        String preset
) {
    public boolean safeZone() {
        return "safezone".equalsIgnoreCase(preset);
    }

    public double centerX() {
        return (minX + maxX + 1.0) * 0.5;
    }

    public double centerZ() {
        return (minZ + maxZ + 1.0) * 0.5;
    }
}
