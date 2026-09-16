package com.theonesabove.minimap;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Keeps TOA Minimap disabled unless the client is connected to The Ones Above
 * and the server has explicitly authorised the mod through the companion plugin.
 *
 * This is an access control/compatibility guard, not DRM. A modified client can
 * remove client-side checks, so the authoritative half is the Paper handshake.
 */
public final class ServerAccessController {
    private static final String ROOT_DOMAIN = "theonesabove.com";

    private volatile boolean hostAllowed;
    private volatile boolean handshakeAuthorized;
    private volatile String connectedHost = "";

    public void register() {
        PayloadTypeRegistry.clientboundPlay().register(ServerAuthPayload.TYPE, ServerAuthPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(ServerAuthPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    // Never accept a handshake from a connection whose address is not ours.
                    if (hostAllowed && payload.authorized()) {
                        handshakeAuthorized = true;
                        System.out.println("[TOA Minimap] Server handshake accepted for " + connectedHost);
                    } else {
                        handshakeAuthorized = false;
                    }
                })
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            reset();
            ServerData server = client.getCurrentServer();
            connectedHost = normalizeHost(server == null ? "" : server.ip);
            hostAllowed = isTheOnesAboveHost(connectedHost);

            if (hostAllowed) {
                System.out.println("[TOA Minimap] TOA hostname recognised (" + connectedHost + "). Waiting for server handshake...");
            } else {
                System.out.println("[TOA Minimap] Disabled on non-TOA server: " + connectedHost);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public boolean isAuthorized(Minecraft client) {
        return client != null
                && client.getCurrentServer() != null
                && hostAllowed
                && handshakeAuthorized;
    }

    public boolean isHostAllowed() {
        return hostAllowed;
    }

    public boolean hasHandshake() {
        return handshakeAuthorized;
    }

    public void showUnavailableMessage(Minecraft client) {
        if (client == null || client.player == null) return;
        String message;
        if (!hostAllowed) {
            message = "TOA Minimap is only available on The Ones Above.";
        } else if (!handshakeAuthorized) {
            message = "TOA Minimap is waiting for server authorisation.";
        } else {
            message = "TOA Minimap is unavailable.";
        }
        client.player.sendSystemMessage(Component.literal(message));
    }

    private void reset() {
        hostAllowed = false;
        handshakeAuthorized = false;
        connectedHost = "";
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

        // Standard hostname:port. Leave IPv6 literals untouched rather than
        // accidentally treating part of the address as a port.
        if (!host.startsWith("[") && host.indexOf(':') == host.lastIndexOf(':')) {
            int colon = host.lastIndexOf(':');
            if (colon > 0) host = host.substring(0, colon);
        } else if (host.startsWith("[") && host.contains("]")) {
            host = host.substring(1, host.indexOf(']'));
        }
        return host;
    }
}
