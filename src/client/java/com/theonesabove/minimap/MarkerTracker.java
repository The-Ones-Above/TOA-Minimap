package com.theonesabove.minimap;

import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps minimap entity markers visually stable and smooth between Minecraft/network updates.
 *
 * Positions are kept as doubles all the way to the render transform.  The HUD renderer only
 * converts them to GUI-space at the very end, so markers do not jump a whole pixel at a time.
 */
public final class MarkerTracker {
    private static final double PLAYER_SMOOTHING_RATE = 18.0;
    private static final double NPC_SMOOTHING_RATE = 12.0;
    private static final double NPC_TARGET_DEADZONE_SQ = 0.05 * 0.05;
    private static final double TELEPORT_SNAP_DISTANCE_SQ = 24.0 * 24.0;
    private static final double MAX_FRAME_SECONDS = 0.050;

    private final Map<UUID, SmoothMarker> players = new HashMap<>();
    private final Map<UUID, SmoothMarker> npcs = new HashMap<>();
    private long lastFrameNanos;

    public Frame update(Minecraft mc, ServerAccessController access) {
        long now = System.nanoTime();
        double dt;
        if (lastFrameNanos == 0L) dt = 1.0 / 60.0;
        else dt = Math.min(MAX_FRAME_SECONDS, Math.max(0.0, (now - lastFrameNanos) / 1_000_000_000.0));
        lastFrameNanos = now;

        Set<UUID> npcIds = new HashSet<>();
        for (NpcMarker npc : access.npcMarkers()) {
            npcIds.add(npc.uuid());
            SmoothMarker marker = npcs.computeIfAbsent(npc.uuid(), id -> SmoothMarker.snap(npc.x(), npc.z()));
            marker.setTarget(npc.x(), npc.z(), true);
            marker.advance(dt, NPC_SMOOTHING_RATE);
        }
        npcs.keySet().removeIf(id -> !npcIds.contains(id));

        Set<UUID> playerIds = new HashSet<>();
        if (mc.level != null) {
            for (var player : mc.level.players()) {
                if (player == mc.player || npcIds.contains(player.getUUID())) continue;
                UUID id = player.getUUID();
                playerIds.add(id);
                SmoothMarker marker = players.computeIfAbsent(id, ignored -> SmoothMarker.snap(player.getX(), player.getZ()));
                marker.setTarget(player.getX(), player.getZ(), false);
                marker.advance(dt, PLAYER_SMOOTHING_RATE);
            }
        }
        players.keySet().removeIf(id -> !playerIds.contains(id));

        return new Frame(Map.copyOf(players), Map.copyOf(npcs));
    }

    public void reset() {
        players.clear();
        npcs.clear();
        lastFrameNanos = 0L;
    }

    public record Frame(Map<UUID, SmoothMarker> players, Map<UUID, SmoothMarker> npcs) {}

    public static final class SmoothMarker {
        private double renderX;
        private double renderZ;
        private double targetX;
        private double targetZ;

        private SmoothMarker(double x, double z) {
            this.renderX = this.targetX = x;
            this.renderZ = this.targetZ = z;
        }

        static SmoothMarker snap(double x, double z) {
            return new SmoothMarker(x, z);
        }

        void setTarget(double x, double z, boolean npc) {
            double targetDx = x - targetX;
            double targetDz = z - targetZ;

            // Citizens can report tiny coordinate changes while visually standing still.
            // Ignore those micro updates completely so the yellow marker is rock solid.
            if (npc && targetDx * targetDx + targetDz * targetDz < NPC_TARGET_DEADZONE_SQ) return;

            targetX = x;
            targetZ = z;

            double renderDx = targetX - renderX;
            double renderDz = targetZ - renderZ;
            if (renderDx * renderDx + renderDz * renderDz > TELEPORT_SNAP_DISTANCE_SQ) {
                renderX = targetX;
                renderZ = targetZ;
            }
        }

        void advance(double dt, double rate) {
            if (dt <= 0.0) return;
            double alpha = 1.0 - Math.exp(-rate * dt);
            renderX += (targetX - renderX) * alpha;
            renderZ += (targetZ - renderZ) * alpha;

            // Avoid endless sub-pixel convergence around a stationary marker.
            if (Math.abs(targetX - renderX) < 0.0005) renderX = targetX;
            if (Math.abs(targetZ - renderZ) < 0.0005) renderZ = targetZ;
        }

        public double x() { return renderX; }
        public double z() { return renderZ; }
    }
}
