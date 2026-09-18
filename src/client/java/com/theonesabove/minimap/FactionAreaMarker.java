package com.theonesabove.minimap;

/**
 * Server-authored faction land shown on the full World Map only.
 *
 * kind: territory, property, hq or turf
 * label: only intended for user-facing area names such as turf names
 * owner: faction display name, used only for hover information
 */
public record FactionAreaMarker(
        String id,
        String kind,
        String label,
        String owner,
        String color,
        int minX,
        int minZ,
        int maxX,
        int maxZ
) {
    public boolean contains(double x, double z) {
        return x >= minX && x < maxX + 1.0 && z >= minZ && z < maxZ + 1.0;
    }

    public double centerX() { return (minX + maxX + 1.0) * 0.5; }
    public double centerZ() { return (minZ + maxZ + 1.0) * 0.5; }
    public boolean turf() { return "turf".equalsIgnoreCase(kind); }
}
