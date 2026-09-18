package com.theonesabove.toaminimapauth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Base64;
import java.util.Collection;

/**
 * Server companion for TOA Minimap.
 *
 * Responsibilities:
 * - authorise the Fabric client on The Ones Above
 * - provide inventory entitlement state for the Navigator's Compass / City Map
 * - publish nearby Citizens NPC positions for yellow minimap dots
 */
public final class TOAMinimapAuthPlugin extends JavaPlugin implements PluginMessageListener {
    public static final String AUTH_CHANNEL = "toaminimap:auth";
    public static final String STATE_CHANNEL = "toaminimap:state";
    public static final String NPC_CHANNEL = "toaminimap:npcs";
    public static final String ZONE_CHANNEL = "toaminimap:zones";
    public static final String FACTION_CHANNEL = "toaminimap:faction_areas";

    private static final double NPC_RADIUS = 256.0;
    private static final int MAX_NPCS_PER_PLAYER = 128;
    private static final double NPC_STABILIZE_DISTANCE_SQ = 0.15 * 0.15;

    private final Set<UUID> authorizedPlayers = new HashSet<>();
    private final Map<UUID, AccessState> lastAccessState = new HashMap<>();
    private final Map<UUID, StableNpcPosition> stableNpcPositions = new HashMap<>();
    private final Map<UUID, String> lastZoneSnapshot = new HashMap<>();
    private final Map<UUID, String> lastFactionSnapshot = new HashMap<>();
    private boolean loggedFactionSource;
    private boolean loggedZoneSource;
    private NamespacedKey itemTypeKey;

    @Override
    public void onEnable() {
        itemTypeKey = new NamespacedKey(this, "item_type");

        getServer().getMessenger().registerIncomingPluginChannel(this, AUTH_CHANNEL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, AUTH_CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, STATE_CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, NPC_CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ZONE_CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, FACTION_CHANNEL);

        // Inventory state is cheap and does not need to be sent every tick.
        Bukkit.getScheduler().runTaskTimer(this, this::broadcastAccessStates, 10L, 10L);
        // NPCs move, so update their minimap positions four times per second.
        Bukkit.getScheduler().runTaskTimer(this, this::broadcastNpcSnapshots, 5L, 5L);
        // Zones change rarely. Send a snapshot once per second and only when it differs.
        Bukkit.getScheduler().runTaskTimer(this, this::broadcastZoneSnapshots, 20L, 20L);
        // Faction land/turf ownership can change at runtime; one-second snapshots are enough.
        Bukkit.getScheduler().runTaskTimer(this, this::broadcastFactionSnapshots, 20L, 20L);

        getLogger().info("TOA Minimap companion ready (auth + access items + Citizens markers + TOAZones overlays).");
        getLogger().info("Channels: " + AUTH_CHANNEL + ", " + STATE_CHANNEL + ", " + NPC_CHANNEL + ", " + ZONE_CHANNEL + ", " + FACTION_CHANNEL);

    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this, AUTH_CHANNEL, this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, AUTH_CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, STATE_CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, NPC_CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, ZONE_CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, FACTION_CHANNEL);
        authorizedPlayers.clear();
        lastAccessState.clear();
        stableNpcPositions.clear();
        lastZoneSnapshot.clear();
        lastFactionSnapshot.clear();
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!AUTH_CHANNEL.equals(channel)) return;
        if (message.length == 0 || message[0] == 0) return;

        getLogger().info("Authorization request received from " + player.getName());
        authorize(player);
    }

    private void authorize(Player player) {
        if (!player.isOnline()) return;
        try {
            player.sendPluginMessage(this, AUTH_CHANNEL, new byte[] { 1 });
            authorizedPlayers.add(player.getUniqueId());
            lastAccessState.remove(player.getUniqueId());
            sendAccessState(player, true);
            sendNpcSnapshot(player, citizensEntities());
            sendZoneSnapshot(player, true);
            sendFactionSnapshot(player, true);
            getLogger().info("Authorization response sent to " + player.getName());
        } catch (Exception ex) {
            getLogger().warning("Could not send TOA Minimap authorization to " + player.getName() + ": " + ex.getMessage());
        }
    }

    private void broadcastAccessStates() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!authorizedPlayers.contains(player.getUniqueId())) continue;
            sendAccessState(player, false);
        }
    }

    private void sendAccessState(Player player, boolean force) {
        AccessState state = inspectAccess(player);
        AccessState previous = lastAccessState.get(player.getUniqueId());
        if (!force && state.equals(previous)) return;

        try {
            player.sendPluginMessage(this, STATE_CHANNEL, new byte[] {
                    (byte)(state.hasCompass ? 1 : 0),
                    (byte)(state.hasCityMap ? 1 : 0)
            });
            lastAccessState.put(player.getUniqueId(), state);
        } catch (Throwable ex) {
            getLogger().warning("Could not send TOA Minimap item state to " + player.getName() + ": " + ex.getMessage());
        }
    }

    private AccessState inspectAccess(Player player) {
        boolean compass = false;
        boolean cityMap = false;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) continue;
            String type = meta.getPersistentDataContainer().get(itemTypeKey, PersistentDataType.STRING);
            if ("minimap_compass".equals(type)) compass = true;
            else if ("city_map".equals(type)) cityMap = true;
            if (compass && cityMap) break;
        }
        return new AccessState(compass, cityMap);
    }

    private void broadcastNpcSnapshots() {
        List<Entity> citizens = citizensEntities();
        Set<UUID> liveNpcIds = new HashSet<>();
        for (Entity entity : citizens) liveNpcIds.add(entity.getUniqueId());
        stableNpcPositions.keySet().removeIf(id -> !liveNpcIds.contains(id));

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!authorizedPlayers.contains(player.getUniqueId())) continue;
            sendNpcSnapshot(player, citizens);
        }
    }

    private void sendNpcSnapshot(Player player, List<Entity> citizens) {
        Location origin = player.getLocation();
        double radiusSq = NPC_RADIUS * NPC_RADIUS;
        StringBuilder out = new StringBuilder();
        int count = 0;

        for (Entity entity : citizens) {
            if (entity == null || !entity.isValid() || entity.getWorld() != player.getWorld()) continue;
            Location raw = entity.getLocation();
            if (raw.distanceSquared(origin) > radiusSq) continue;
            StableNpcPosition stable = stabilizeNpc(entity, raw);

            if (count++ >= MAX_NPCS_PER_PLAYER) break;
            out.append(entity.getUniqueId())
                    .append(',').append(trimDouble(stable.x()))
                    .append(',').append(trimDouble(stable.z()))
                    .append('\n');
        }

        try {
            player.sendPluginMessage(this, NPC_CHANNEL, out.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ex) {
            getLogger().warning("Could not send Citizens markers to " + player.getName() + ": " + ex.getMessage());
        }
    }


    /**
     * Citizens entities can report tiny coordinate changes even when an NPC is visually stationary.
     * Keep its minimap position fixed until it has actually moved a meaningful distance.
     */
    private StableNpcPosition stabilizeNpc(Entity entity, Location raw) {
        UUID id = entity.getUniqueId();
        UUID worldId = raw.getWorld().getUID();
        StableNpcPosition previous = stableNpcPositions.get(id);
        if (previous != null && previous.worldId().equals(worldId)) {
            double dx = raw.getX() - previous.x();
            double dz = raw.getZ() - previous.z();
            if (dx * dx + dz * dz < NPC_STABILIZE_DISTANCE_SQ) return previous;
        }
        StableNpcPosition next = new StableNpcPosition(worldId, raw.getX(), raw.getZ());
        stableNpcPositions.put(id, next);
        return next;
    }

    /** Citizens is optional and accessed reflectively so this plugin has no hard dependency. */
    private List<Entity> citizensEntities() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) return List.of();
        try {
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Object registry = api.getMethod("getNPCRegistry").invoke(null);
            if (!(registry instanceof Iterable<?> iterable)) return List.of();

            List<Entity> result = new ArrayList<>();
            for (Object npc : iterable) {
                if (npc == null) continue;
                Method getEntity = npc.getClass().getMethod("getEntity");
                Object value = getEntity.invoke(npc);
                if (value instanceof Entity entity && entity.isValid()) result.add(entity);
            }
            return result;
        } catch (Throwable ex) {
            // Citizens API changes should never break authorization/map access.
            return List.of();
        }
    }


    private void broadcastZoneSnapshots() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!authorizedPlayers.contains(player.getUniqueId())) continue;
            sendZoneSnapshot(player, false);
        }
    }

    /**
     * Publishes the rectangular TOAZones regions for the player's current Bukkit world.
     * TOAZones is optional and accessed reflectively, so this companion remains loadable
     * even if the zones plugin is temporarily missing or reloading.
     */
    private void sendZoneSnapshot(Player player, boolean force) {
        String payload = buildZoneSnapshot(player);
        String previous = lastZoneSnapshot.get(player.getUniqueId());
        if (!force && payload.equals(previous)) return;

        try {
            player.sendPluginMessage(this, ZONE_CHANNEL, payload.getBytes(StandardCharsets.UTF_8));
            lastZoneSnapshot.put(player.getUniqueId(), payload);
        } catch (Throwable ex) {
            getLogger().warning("Could not send TOAZones snapshot to " + player.getName() + ": " + ex.getMessage());
        }
    }

    private String buildZoneSnapshot(Player player) {
        Plugin zonesPlugin = Bukkit.getPluginManager().getPlugin("TOAZones");
        if (zonesPlugin == null || !zonesPlugin.isEnabled()) {
            if (!loggedZoneSource) {
                getLogger().warning("TOAZones is not enabled; World Map zone overlays will be empty.");
                loggedZoneSource = true;
            }
            return "";
        }

        try {
            Object api = zonesPlugin.getClass().getMethod("getAPI").invoke(zonesPlugin);
            Object rawZones = api.getClass().getMethod("getAllZones").invoke(api);
            if (!(rawZones instanceof Collection<?> zones)) return "";

            String currentWorld = player.getWorld().getName();
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            StringBuilder out = new StringBuilder();

            for (Object zone : zones) {
                if (zone == null) continue;
                Class<?> type = zone.getClass();

                String world = String.valueOf(type.getMethod("getWorld").invoke(zone));
                if (!currentWorld.equals(world)) continue;

                String id = String.valueOf(type.getMethod("getId").invoke(zone));
                String name = String.valueOf(type.getMethod("getName").invoke(zone));
                String preset = String.valueOf(type.getMethod("getPreset").invoke(zone));
                int minX = ((Number) type.getMethod("getMinX").invoke(zone)).intValue();
                int minZ = ((Number) type.getMethod("getMinZ").invoke(zone)).intValue();
                int maxX = ((Number) type.getMethod("getMaxX").invoke(zone)).intValue();
                int maxZ = ((Number) type.getMethod("getMaxZ").invoke(zone)).intValue();

                out.append(encoder.encodeToString(id.getBytes(StandardCharsets.UTF_8))).append('\t')
                        .append(encoder.encodeToString(name.getBytes(StandardCharsets.UTF_8))).append('\t')
                        .append(minX).append('\t')
                        .append(minZ).append('\t')
                        .append(maxX).append('\t')
                        .append(maxZ).append('\t')
                        .append(preset == null ? "custom" : preset)
                        .append('\n');
            }

            String snapshot = out.toString();
            if (!loggedZoneSource) {
                int count = snapshot.isBlank() ? 0 : snapshot.split("\\n").length;
                getLogger().info("TOAZones integration active: publishing " + count
                        + " zone(s) for world " + currentWorld + ".");
                loggedZoneSource = true;
            }
            return snapshot;
        } catch (Throwable ex) {
            if (!loggedZoneSource) {
                getLogger().warning("Could not read TOAZones API: "
                        + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                loggedZoneSource = true;
            }
            // A zones-plugin reload/API change must never affect minimap authorization.
            return "";
        }
    }


    private void broadcastFactionSnapshots() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!authorizedPlayers.contains(player.getUniqueId())) continue;
            sendFactionSnapshot(player, false);
        }
    }

    private void sendFactionSnapshot(Player player, boolean force) {
        String payload = buildFactionSnapshot(player);
        String previous = lastFactionSnapshot.get(player.getUniqueId());
        if (!force && payload.equals(previous)) return;

        try {
            player.sendPluginMessage(this, FACTION_CHANNEL, payload.getBytes(StandardCharsets.UTF_8));
            lastFactionSnapshot.put(player.getUniqueId(), payload);
        } catch (Throwable ex) {
            getLogger().warning("Could not send TOAFactions map areas to "
                    + player.getName() + ": " + ex.getMessage());
        }
    }

    /**
     * Reads TOAFactions reflectively so this companion has no hard compile-time
     * dependency on the faction plugin.
     */
    private String buildFactionSnapshot(Player player) {
        Plugin factionsPlugin = Bukkit.getPluginManager().getPlugin("TOAFactions");
        if (factionsPlugin == null || !factionsPlugin.isEnabled()) {
            if (!loggedFactionSource) {
                getLogger().warning("TOAFactions is not enabled; faction World Map overlays will be empty.");
                loggedFactionSource = true;
            }
            return "";
        }

        try {
            String currentWorld = player.getWorld().getName();
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            StringBuilder out = new StringBuilder();

            Object factionManager = factionsPlugin.getClass().getMethod("getFactionManager").invoke(factionsPlugin);
            Object allFactionsRaw = factionManager.getClass().getMethod("getAllFactions").invoke(factionManager);

            if (allFactionsRaw instanceof Collection<?> factions) {
                for (Object faction : factions) {
                    if (faction == null) continue;
                    Class<?> ft = faction.getClass();

                    String factionId = String.valueOf(ft.getMethod("getId").invoke(faction));
                    String owner = String.valueOf(ft.getMethod("getDisplayName").invoke(faction));
                    String factionColor = String.valueOf(ft.getMethod("getColor").invoke(faction));

                    boolean hasRegion = (Boolean) ft.getMethod("hasRegion").invoke(faction);
                    if (hasRegion) {
                        String world = String.valueOf(ft.getMethod("getHqWorld").invoke(faction));
                        if (currentWorld.equals(world)) {
                            appendFactionArea(out, encoder,
                                    "hq_" + factionId, "hq", "", owner, factionColor,
                                    ((Number)ft.getMethod("getRegionMinX").invoke(faction)).intValue(),
                                    ((Number)ft.getMethod("getRegionMinZ").invoke(faction)).intValue(),
                                    ((Number)ft.getMethod("getRegionMaxX").invoke(faction)).intValue(),
                                    ((Number)ft.getMethod("getRegionMaxZ").invoke(faction)).intValue());
                        }
                    }

                    Object territoriesRaw = ft.getMethod("getTerritories").invoke(faction);
                    if (territoriesRaw instanceof Map<?, ?> territories) {
                        for (Object territory : territories.values()) {
                            if (territory == null) continue;
                            Class<?> tt = territory.getClass();
                            String world = String.valueOf(tt.getMethod("getWorld").invoke(territory));
                            if (!currentWorld.equals(world)) continue;

                            String id = String.valueOf(tt.getMethod("getId").invoke(territory));
                            String name = String.valueOf(tt.getMethod("getName").invoke(territory));
                            boolean property = (Boolean) tt.getMethod("isProperty").invoke(territory);
                            String kind = property ? "property" : "territory";

                            appendFactionArea(out, encoder,
                                    kind + "_" + factionId + "_" + id,
                                    kind, name, owner, factionColor,
                                    ((Number)tt.getMethod("getMinX").invoke(territory)).intValue(),
                                    ((Number)tt.getMethod("getMinZ").invoke(territory)).intValue(),
                                    ((Number)tt.getMethod("getMaxX").invoke(territory)).intValue(),
                                    ((Number)tt.getMethod("getMaxZ").invoke(territory)).intValue());
                        }
                    }
                }
            }

            Object turfManager = factionsPlugin.getClass().getMethod("getTurfManager").invoke(factionsPlugin);
            Object turfsRaw = turfManager.getClass().getMethod("getAllTurfs").invoke(turfManager);
            if (turfsRaw instanceof Collection<?> turfs) {
                for (Object turf : turfs) {
                    if (turf == null) continue;
                    Class<?> tt = turf.getClass();
                    String world = String.valueOf(tt.getMethod("getWorld").invoke(turf));
                    if (!currentWorld.equals(world)) continue;

                    String id = String.valueOf(tt.getMethod("getId").invoke(turf));
                    String name = String.valueOf(tt.getMethod("getName").invoke(turf));
                    Object ownerIdRaw = tt.getMethod("getOwnerFactionId").invoke(turf);
                    String owner = "Unclaimed";
                    String turfColor = "GRAY";

                    if (ownerIdRaw != null) {
                        Object ownerFaction = factionManager.getClass()
                                .getMethod("getFaction", String.class)
                                .invoke(factionManager, String.valueOf(ownerIdRaw));
                        if (ownerFaction != null) {
                            owner = String.valueOf(ownerFaction.getClass()
                                    .getMethod("getDisplayName").invoke(ownerFaction));
                            turfColor = String.valueOf(ownerFaction.getClass()
                                    .getMethod("getColor").invoke(ownerFaction));
                        }
                    }

                    appendFactionArea(out, encoder,
                            "turf_" + id, "turf", name, owner, turfColor,
                            ((Number)tt.getMethod("getMinX").invoke(turf)).intValue(),
                            ((Number)tt.getMethod("getMinZ").invoke(turf)).intValue(),
                            ((Number)tt.getMethod("getMaxX").invoke(turf)).intValue(),
                            ((Number)tt.getMethod("getMaxZ").invoke(turf)).intValue());
                }
            }

            String snapshot = out.toString();
            if (!loggedFactionSource) {
                int count = snapshot.isBlank() ? 0 : snapshot.split("\\n").length;
                getLogger().info("TOAFactions integration active: publishing " + count
                        + " faction area(s) for world " + currentWorld + ".");
                loggedFactionSource = true;
            }
            return snapshot;
        } catch (Throwable ex) {
            if (!loggedFactionSource) {
                getLogger().warning("Could not read TOAFactions map data: "
                        + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                loggedFactionSource = true;
            }
            return "";
        }
    }

    private static void appendFactionArea(StringBuilder out, Base64.Encoder encoder,
                                          String id, String kind, String label, String owner,
                                          String color,
                                          int minX, int minZ, int maxX, int maxZ) {
        out.append(encoder.encodeToString(id.getBytes(StandardCharsets.UTF_8))).append('\t')
                .append(kind).append('\t')
                .append(encoder.encodeToString((label == null ? "" : label).getBytes(StandardCharsets.UTF_8))).append('\t')
                .append(encoder.encodeToString((owner == null ? "" : owner).getBytes(StandardCharsets.UTF_8))).append('\t')
                .append(color == null || color.isBlank() ? "WHITE" : color).append('\t')
                .append(minX).append('\t')
                .append(minZ).append('\t')
                .append(maxX).append('\t')
                .append(maxZ).append('\n');
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("toaminimap")) return false;
        if (!sender.hasPermission("toaminimap.admin")) {
            sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sendUsage(sender);
            return true;
        }

        Player target;
        if (args.length >= 3) target = Bukkit.getPlayerExact(args[2]);
        else target = sender instanceof Player p ? p : null;
        if (target == null) {
            sender.sendMessage(Component.text("Player not found. From console, specify a player name.", NamedTextColor.RED));
            return true;
        }

        String type = args[1].toLowerCase();
        switch (type) {
            case "compass" -> give(target, createCompass());
            case "map", "citymap", "city_map" -> give(target, createCityMap());
            case "both" -> {
                give(target, createCompass());
                give(target, createCityMap());
            }
            default -> {
                sendUsage(sender);
                return true;
            }
        }

        sendAccessState(target, true);
        sender.sendMessage(Component.text("TOA Minimap item(s) given to " + target.getName() + ".", NamedTextColor.GREEN));
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("/toaminimap give <compass|map|both> [player]", NamedTextColor.YELLOW));
    }

    private void give(Player player, ItemStack stack) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        for (ItemStack extra : overflow.values()) player.getWorld().dropItemNaturally(player.getLocation(), extra);
    }

    private ItemStack createCompass() {
        ItemStack stack = new ItemStack(Material.COMPASS);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("Navigator's Compass", NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("Reveals your TOA minimap.", NamedTextColor.GRAY),
                Component.text("Keep this in your inventory.", NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(itemTypeKey, PersistentDataType.STRING, "minimap_compass");
        // Uses the vanilla compass appearance by default; the persistent ID makes it a
        // distinct TOA item and lets an Oraxen/resource-pack model be layered on later.
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createCityMap() {
        ItemStack stack = new ItemStack(Material.MAP);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("City Map", NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("Grants access to the TOA World Map.", NamedTextColor.GRAY),
                Component.text("Press X while carrying it.", NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(itemTypeKey, PersistentDataType.STRING, "city_map");
        stack.setItemMeta(meta);
        return stack;
    }

    private static String trimDouble(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private record AccessState(boolean hasCompass, boolean hasCityMap) {}
    private record StableNpcPosition(UUID worldId, double x, double z) {}
}
