package com.theonesabove.minimap;

import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks the official GitHub latest release without blocking the Minecraft thread. */
public final class UpdateChecker {
    public static final String LATEST_RELEASE_API = "https://api.github.com/repos/The-Ones-Above/TOA-Minimap/releases/latest";
    public static final String LATEST_DOWNLOAD_URL = "https://github.com/The-Ones-Above/TOA-Minimap/releases/latest/download/TOA-Minimap.jar";

    private static final Pattern TAG_PATTERN = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private volatile String availableVersion;
    private volatile boolean checkStarted;
    private boolean popupShown;

    public void start() {
        if (checkStarted) return;
        checkStarted = true;

        String current = currentVersion();
        HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_RELEASE_API))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "TOA-Minimap/" + current)
                .GET()
                .build();

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) return;
                    Matcher matcher = TAG_PATTERN.matcher(response.body());
                    if (!matcher.find()) return;
                    String latest = normalizeVersion(matcher.group(1));
                    if (isNewer(latest, current)) {
                        availableVersion = latest;
                        System.out.println("[TOA Minimap] Update available: " + current + " -> " + latest);
                    }
                })
                .exceptionally(ex -> {
                    System.out.println("[TOA Minimap] Update check skipped: " + ex.getMessage());
                    return null;
                });
    }

    /** Called from the client tick. Shows the notice once, after the user is actually in game. */
    public void tick(net.minecraft.client.Minecraft client) {
        if (popupShown || availableVersion == null || client == null || client.player == null) return;
        if (client.gui.screen() != null) return;
        popupShown = true;
        client.gui.setScreen(new UpdateAvailableScreen(currentVersion(), availableVersion));
    }

    private static String currentVersion() {
        return FabricLoader.getInstance().getModContainer(ToaMinimapClient.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .map(UpdateChecker::normalizeVersion)
                .orElse("0.0.0");
    }

    static String normalizeVersion(String version) {
        if (version == null) return "0.0.0";
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        int dash = v.indexOf('-');
        if (dash >= 0) v = v.substring(0, dash);
        return v;
    }

    static boolean isNewer(String candidate, String current) {
        int[] a = parts(candidate);
        int[] b = parts(current);
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) return av > bv;
        }
        return false;
    }

    private static int[] parts(String version) {
        String[] raw = normalizeVersion(version).split("\\.");
        int[] result = new int[raw.length];
        for (int i = 0; i < raw.length; i++) {
            try { result[i] = Integer.parseInt(raw[i].replaceAll("[^0-9].*$", "")); }
            catch (RuntimeException ignored) { result[i] = 0; }
        }
        return result;
    }
}
