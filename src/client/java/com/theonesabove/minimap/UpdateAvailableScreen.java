package com.theonesabove.minimap;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.awt.Desktop;
import java.net.URI;

/** Simple non-blocking update notice. Enter opens the permanent latest-release download URL. */
public final class UpdateAvailableScreen extends Screen {
    private final String currentVersion;
    private final String latestVersion;

    public UpdateAvailableScreen(String currentVersion, String latestVersion) {
        super(Component.literal("TOA Minimap Update"));
        this.currentVersion = currentVersion;
        this.latestVersion = latestVersion;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.fill(0, 0, width, height, 0xA8000000);

        int boxW = Math.min(330, Math.max(250, width - 40));
        int boxH = 112;
        int left = (width - boxW) / 2;
        int top = (height - boxH) / 2;
        int right = left + boxW;
        int bottom = top + boxH;

        g.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF555555);
        g.fill(left, top, right, bottom, 0xF5151515);

        String title = "TOA Minimap update available";
        g.text(font, title, (width - font.width(title)) / 2, top + 15, 0xFFFFFFFF, false);

        String versions = "Installed " + currentVersion + "  •  Latest " + latestVersion;
        g.text(font, versions, (width - font.width(versions)) / 2, top + 38, 0xFFB6B6B6, false);

        String line1 = "Press Enter to download the latest version";
        g.text(font, line1, (width - font.width(line1)) / 2, top + 65, 0xFFFFD166, false);

        String line2 = "Esc to continue with this version";
        g.text(font, line2, (width - font.width(line2)) / 2, top + 82, 0xFF777777, false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI.create(UpdateChecker.LATEST_DOWNLOAD_URL));
                }
            } catch (Throwable ignored) {}
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
}
