package com.theonesabove.toaminimapauth;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

/** Reliable request/response authorization bridge for TOA Minimap. */
public final class TOAMinimapAuthPlugin extends JavaPlugin implements Listener, PluginMessageListener {
    public static final String CHANNEL = "toaminimap:auth";

    @Override
    public void onEnable() {
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("TOA Minimap authorization ready on channel " + CHANNEL);

        // Backwards-compatible push for older clients. 2.7.1+ also sends a request.
        for (Player player : Bukkit.getOnlinePlayers()) scheduleLegacyHandshake(player);
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL, this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleLegacyHandshake(event.getPlayer());
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!CHANNEL.equals(channel)) return;
        if (message.length == 0 || message[0] == 0) return;

        getLogger().info("Authorization request received from " + player.getName());
        authorize(player);
    }

    private void authorize(Player player) {
        if (!player.isOnline()) return;
        try {
            player.sendPluginMessage(this, CHANNEL, new byte[] { 1 });
            getLogger().info("Authorization response sent to " + player.getName());
        } catch (Exception ex) {
            getLogger().warning("Could not send TOA Minimap authorization to " + player.getName() + ": " + ex.getMessage());
        }
    }

    private void scheduleLegacyHandshake(Player player) {
        Bukkit.getScheduler().runTaskLater(this, () -> authorize(player), 40L);
    }
}
