package com.theonesabove.minimap;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ToaMinimapClient implements ClientModInitializer {
    public static final String MOD_ID = "toaminimap";
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "controls")
    );

    private MinimapConfig config;
    private MinimapHud hud;
    private MapViewRenderer renderer;
    private ClientMapCache cache;
    private ServerAccessController access;

    @Override
    public void onInitializeClient() {
        config = MinimapConfig.load();
        access = new ServerAccessController();
        access.register();
        cache = new ClientMapCache(config);
        renderer = new MapViewRenderer(cache);
        hud = new MinimapHud(config, renderer);

        KeyMapping edit = key("key.toaminimap.edit_layout", GLFW.GLFW_KEY_M);
        KeyMapping world = key("key.toaminimap.open_world_map", GLFW.GLFW_KEY_X);
        KeyMapping toggle = key("key.toaminimap.toggle_minimap", GLFW.GLFW_KEY_UNKNOWN);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            access.tick(client);
            boolean authorized = access.isAuthorized(client);

            if (!authorized) {
                // If authorization is lost while a TOA Minimap screen is open
                // (disconnect/server switch), close it immediately and stop all
                // map generation/cache updates.
                if (client.gui.screen() instanceof WorldMapScreen || client.gui.screen() instanceof MinimapEditScreen) {
                    client.gui.setScreen(null);
                    hud.setEditing(false);
                }
            } else {
                cache.tick(client);
            }

            while (edit.consumeClick()) {
                if (!authorized) {
                    access.showUnavailableMessage(client);
                    continue;
                }
                if (client.gui.screen() instanceof MinimapEditScreen) {
                    client.gui.setScreen(null);
                    hud.setEditing(false);
                    config.save();
                } else if (client.gui.screen() == null && client.player != null) {
                    client.gui.setScreen(new MinimapEditScreen(config, hud));
                }
            }

            while (world.consumeClick()) {
                if (!authorized) {
                    access.showUnavailableMessage(client);
                    continue;
                }
                if (client.gui.screen() instanceof WorldMapScreen) client.gui.setScreen(null);
                else if (client.gui.screen() == null && client.player != null) {
                    client.gui.setScreen(new WorldMapScreen(config, renderer, world));
                }
            }

            while (toggle.consumeClick()) {
                if (!authorized) {
                    access.showUnavailableMessage(client);
                    continue;
                }
                config.enabled = !config.enabled;
                config.save();
            }
        });

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "minimap"),
                (graphics, delta) -> { if (access.isAuthorized(net.minecraft.client.Minecraft.getInstance())) hud.render(graphics); }
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { cache.close(); }
            catch (Exception ignored) {}
        }));
    }

    private KeyMapping key(String translationKey, int defaultKey) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping(translationKey, InputConstants.Type.KEYSYM, defaultKey, CATEGORY)
        );
    }
}
