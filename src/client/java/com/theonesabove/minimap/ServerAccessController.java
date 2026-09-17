package com.theonesabove.minimap;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Keeps TOA Minimap disabled unless the client is connected to The Ones Above
 * and the server explicitly authorises the mod through the companion plugin.
 * Also receives server-authoritative access-item and Citizens NPC state.
 */
public final class ServerAccessController {
    private static final String ROOT_DOMAIN = "theonesabove.com";
    private static final long RETRY_MS = 2_000L;

    private volatile boolean hostAllowed;
    private volatile boolean handshakeAuthorized;
    private volatile boolean hasCompass;
    private volatile boolean hasCityMap;
    private volatile String connectedHost = "";
    private volatile List<NpcMarker> npcMarkers = List.of();
    private long nextRequestAt;

    public void register() {
        PayloadTypeRegistry.clientboundPlay().register(ServerAuthPayload.TYPE, ServerAuthPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientAuthRequestPayload.TYPE, ClientAuthRequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ItemAccessPayload.TYPE, ItemAccessPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(NpcMarkersPayload.TYPE, NpcMarkersPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(ServerAuthPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (hostAllowed && payload.authorized()) {
                        handshakeAuthorized = true;
                        System.out.println("[TOA Minimap] Server authorization accepted for " + connectedHost);
                    } else {
                        handshakeAuthorized = false;
                    }
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(ItemAccessPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    hasCompass = payload.hasCompass();
                    hasCityMap = payload.hasCityMap();
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(NpcMarkersPayload.TYPE, (payload, context) ->
                context.client().execute(() -> npcMarkers = parseNpcMarkers(payload.data()))
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            reset();
            ServerData server = client.getCurrentServer();
            connectedHost = normalizeHost(server == null ? "" : server.ip);
            hostAllowed = isTheOnesAboveHost(connectedHost);

            if (hostAllowed) {
                System.out.println("[TOA Minimap] TOA hostname recognised (" + connectedHost + "). Requesting server authorization...");
                nextRequestAt = 0L;
            } else {
                System.out.println("[TOA Minimap] Disabled on non-TOA server: " + connectedHost);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    /** Called from the client tick. Retries until the Paper companion replies. */
    public void tick(Minecraft client) {
        if (!hostAllowed || handshakeAuthorized || client == null || client.player == null || client.getConnection() == null) return;

        long now = System.currentTimeMillis();
        if (now < nextRequestAt) return;
        nextRequestAt = now + RETRY_MS;

        try {
            ClientPlayNetworking.send(new ClientAuthRequestPayload(true));
            System.out.println("[TOA Minimap] Sent authorization request to server on toaminimap:auth");
        } catch (Throwable t) {
            System.out.println("[TOA Minimap] Authorization request could not be sent yet: " + t.getMessage());
        }
    }

    public boolean isAuthorized(Minecraft client) {
        return client != null
                && client.getCurrentServer() != null
                && hostAllowed
                && handshakeAuthorized;
    }

    public boolean hasCompass() { return hasCompass; }
    public boolean hasCityMap() { return hasCityMap; }
    public boolean hasAnyMapItem() { return hasCompass || hasCityMap; }
    public List<NpcMarker> npcMarkers() { return npcMarkers; }

    public void showUnavailableMessage(Minecraft client) {
        if (client == null || client.player == null) return;
        String message;
        if (!hostAllowed) message = "TOA Minimap is only available on The Ones Above.";
        else if (!handshakeAuthorized) message = "TOA Minimap is waiting for server authorisation.";
        else message = "TOA Minimap is unavailable.";
        client.player.sendSystemMessage(Component.literal(message));
    }

    public void showCompassRequired(Minecraft client) {
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal("You need a Navigator's Compass to use the minimap."));
        }
    }

    public void showCityMapRequired(Minecraft client) {
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal("You need a City Map to open the world map."));
        }
    }

    private void reset() {
        hostAllowed = false;
        handshakeAuthorized = false;
        hasCompass = false;
        hasCityMap = false;
        connectedHost = "";
        npcMarkers = List.of();
        nextRequestAt = 0L;
    }

    private static List<NpcMarker> parseNpcMarkers(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<NpcMarker> result = new ArrayList<>();
        String[] rows = raw.split("\\n");
        for (String row : rows) {
            if (row == null || row.isBlank()) continue;
            String[] parts = row.split(",", 3);
            if (parts.length != 3) continue;
            try {
                result.add(new NpcMarker(UUID.fromString(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2])));
            } catch (RuntimeException ignored) {}
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean isTheOnesAboveHost(String host) {
        if (host == null || host.isBlank()) return false;
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals(ROOT_DOMAIN) || h.endsWith("." + ROOT_DOMAIN);
    }

    private static String normalizeHost(String address) {
        if (address == null) return "";
        String host = address.trim().toLowerCase(Locale.ROOT);
        int scheme = host.indexOf("://");
        if (scheme >= 0) host = host.substring(scheme + 3);
        int slash = host.indexOf('/');
        if (slash >= 0) host = host.substring(0, slash);
        if (!host.startsWith("[") && host.indexOf(':') == host.lastIndexOf(':')) {
            int colon = host.lastIndexOf(':');
            if (colon > 0) host = host.substring(0, colon);
        } else if (host.startsWith("[") && host.contains("]")) {
            host = host.substring(1, host.indexOf(']'));
        }
        return host;
    }
}
