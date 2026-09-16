package com.theonesabove.minimap;

/**
 * Cartographic top-down palette tuned for a darker, cleaner Xaero/Dynmap-like look.
 * One cached pixel still equals one world block; visual detail comes from material
 * colour, structured texture, terrain relief and edge contrast rather than blur.
 */
public final class BlockColorPalette {
    private BlockColorPalette() {}

    public static int color(String id, int y,
                            int northY, int southY, int westY, int eastY,
                            int worldX, int worldZ) {
        String s = id == null ? "" : id.toLowerCase();
        int rgb = baseColor(s);

        // Height gradient approximates a fixed NW light source. This makes hills,
        // roofs and walls readable without changing the actual top-down geometry.
        double dx = (eastY - westY) * 0.5;
        double dz = (southY - northY) * 0.5;
        double hill = (-dx - dz) * 0.032;
        hill = clamp(hill, -0.16, 0.16);

        int maxEdge = Math.max(Math.max(Math.abs(y - northY), Math.abs(y - southY)),
                Math.max(Math.abs(y - westY), Math.abs(y - eastY)));

        // Fine edge relief gives buildings a clearer silhouette. Keep it subtle:
        // this is shading, not an artificial outline around every block.
        double edge = 0.0;
        if (maxEdge >= 1) {
            int brightSides = (y > northY ? 1 : 0) + (y > westY ? 1 : 0);
            int darkSides = (y < southY ? 1 : 0) + (y < eastY ? 1 : 0);
            edge += brightSides * 0.018;
            edge -= darkSides * 0.014;
        }
        if (maxEdge >= 3) edge *= 1.35;

        // Structured material texture instead of per-pixel random noise. Random grain
        // made grass/flowers look like TV static at world-map scale.
        double texture = materialTexture(s, worldX, worldZ);

        if (s.contains("water")) {
            hill *= 0.22;
            edge *= 0.20;
            texture *= 0.45;
        }

        double factor = clamp(0.90 + hill + edge + texture, 0.64, 1.16);
        int shaded = shade(rgb, factor);

        // Natural terrain benefits from a slightly darker cartographic grade;
        // pale construction materials stay clean and bright.
        if (isNatural(s)) shaded = grade(shaded, 0.93, 0.92);
        else shaded = grade(shaded, 0.99, 0.96);

        return shaded;
    }

    private static int baseColor(String s) {
        // Water / terrain
        if (s.contains("water")) return 0x315A85;
        if (s.contains("lava")) return 0xD85B22;
        if (s.contains("grass_block")) return 0x557A3D;
        if (s.contains("moss")) return 0x536F3C;
        if (s.contains("leaves")) {
            if (s.contains("spruce")) return 0x284737;
            if (s.contains("birch")) return 0x4F7043;
            if (s.contains("cherry")) return 0x5C6F48;
            return 0x345B35;
        }
        if (s.contains("vine")) return 0x385E34;
        if (s.contains("sandstone")) return 0xC6B77D;
        if (s.contains("red_sand")) return 0xA85D35;
        if (s.contains("sand")) return 0xCDBB78;
        if (s.contains("gravel")) return 0x74716D;
        if (s.contains("snow")) return 0xE7EAEB;
        if (s.contains("packed_ice") || s.contains("blue_ice")) return 0x6FA7C9;
        if (s.contains("ice")) return 0x8FC1D9;
        if (s.contains("mud")) return 0x4A403A;
        if (s.contains("podzol")) return 0x65462E;
        if (s.contains("coarse_dirt")) return 0x72513A;
        if (s.contains("dirt_path")) return 0x8B744D;
        if (s.contains("farmland")) return 0x5E432D;
        if (s.contains("dirt")) return 0x715039;
        if (s.contains("clay")) return 0x90979A;

        // Stone / construction
        if (s.contains("quartz")) return 0xDDD9D2;
        if (s.contains("calcite")) return 0xD3D0C9;
        if (s.contains("smooth_stone")) return 0x969693;
        if (s.contains("deepslate")) return 0x45464A;
        if (s.contains("blackstone")) return 0x35343A;
        if (s.contains("stone_brick")) return 0x777775;
        if (s.contains("cobblestone")) return 0x6D6B67;
        if (s.contains("andesite")) return 0x82817E;
        if (s.contains("diorite")) return 0xADACA8;
        if (s.contains("granite")) return 0x8A6255;
        if (s.contains("stone")) return 0x747472;
        if (s.contains("tuff")) return 0x626A63;
        if (s.contains("basalt")) return 0x4B4C4D;
        if (s.contains("brick")) return 0x945044;
        if (s.contains("terracotta")) return dyeColor(s, 0x8B5948);
        if (s.contains("concrete_powder")) return dyeColor(s, 0x707070);
        if (s.contains("concrete")) return dyeColor(s, 0x707070);
        if (s.contains("wool") || s.contains("carpet")) return dyeColor(s, 0xADADAA);

        // Woods
        if (s.contains("dark_oak")) return 0x44301F;
        if (s.contains("spruce")) return 0x654833;
        if (s.contains("birch")) return 0xC5B983;
        if (s.contains("jungle")) return 0x946B48;
        if (s.contains("acacia")) return 0xA85F42;
        if (s.contains("mangrove")) return 0x6D383B;
        if (s.contains("cherry")) return 0xC89DA8;
        if (s.contains("bamboo")) return 0xA59A55;
        if (s.contains("crimson")) return 0x6D334C;
        if (s.contains("warped")) return 0x32736D;
        if (s.contains("oak")) return 0x9D7B4F;

        // Metals / decorative
        if (s.contains("oxidized_copper")) return 0x438A76;
        if (s.contains("weathered_copper")) return 0x61886D;
        if (s.contains("exposed_copper")) return 0x976B52;
        if (s.contains("copper")) return 0xA95F45;
        if (s.contains("gold")) return 0xD4B73F;
        if (s.contains("iron")) return 0xBABAB7;
        if (s.contains("prismarine")) return 0x57978C;
        if (s.contains("sea_lantern")) return 0xCBD9CA;
        if (s.contains("glowstone")) return 0xB88A4A;
        if (s.contains("glass")) return dyeColor(s, 0x9CB3B7);
        if (s.contains("obsidian")) return 0x272334;
        if (s.contains("amethyst")) return 0x76599A;

        // Other dimensions / common special surfaces
        if (s.contains("netherrack")) return 0x6F3432;
        if (s.contains("nether_brick")) return 0x43292C;
        if (s.contains("end_stone")) return 0xC4C58B;
        if (s.contains("purpur")) return 0x9A6C94;

        int dyed = dyeColor(s, Integer.MIN_VALUE);
        if (dyed != Integer.MIN_VALUE) return dyed;

        // Unknown/custom blocks should never explode into bright random colours.
        // A deterministic muted neutral still differentiates neighbouring materials.
        return mutedFallback(s);
    }

    private static double materialTexture(String s, int x, int z) {
        int checker2 = ((x ^ z) & 1) == 0 ? 1 : -1;
        int checker4 = (((x >> 1) + (z >> 1)) & 1) == 0 ? 1 : -1;

        if (s.contains("leaves")) return checker2 * 0.022 + checker4 * 0.010;
        if (s.contains("grass_block") || s.contains("moss")) return checker4 * 0.010;
        if (s.contains("stone") || s.contains("cobble") || s.contains("deepslate") || s.contains("gravel"))
            return checker2 * 0.007;
        if (s.contains("planks") || s.contains("wood") || s.contains("log"))
            return ((x & 3) == 0 ? -0.012 : 0.004);
        if (s.contains("water")) return (((x + z) & 3) == 0 ? 0.010 : -0.003);
        if (s.contains("concrete") || s.contains("quartz") || s.contains("glass")) return checker4 * 0.003;
        return checker4 * 0.004;
    }

    private static boolean isNatural(String s) {
        return s.contains("grass") || s.contains("leaves") || s.contains("moss") || s.contains("dirt")
                || s.contains("podzol") || s.contains("sand") || s.contains("gravel") || s.contains("water")
                || s.contains("snow") || s.contains("ice") || s.contains("mud") || s.contains("stone")
                || s.contains("deepslate") || s.contains("tuff");
    }

    private static int dyeColor(String s, int fallback) {
        if (s.contains("light_blue")) return 0x559DB2;
        if (s.contains("light_gray") || s.contains("light_grey")) return 0x969692;
        if (s.contains("white")) return 0xDDDDDA;
        if (s.contains("orange")) return 0xC57231;
        if (s.contains("magenta")) return 0x99489F;
        if (s.contains("yellow")) return 0xCAB03D;
        if (s.contains("lime")) return 0x68A53A;
        if (s.contains("pink")) return 0xBE7B8E;
        if (s.contains("gray") || s.contains("grey")) return 0x5C6062;
        if (s.contains("cyan")) return 0x337785;
        if (s.contains("purple")) return 0x6E478F;
        if (s.contains("blue")) return 0x3E5794;
        if (s.contains("brown")) return 0x694936;
        if (s.contains("green")) return 0x4E6632;
        if (s.contains("red")) return 0x913A32;
        if (s.contains("black")) return 0x262626;
        return fallback;
    }

    private static int mutedFallback(String s) {
        int h = s.hashCode();
        int base = 92 + ((h >>> 17) & 31); // 92..123
        int r = base + ((h >>> 11) & 9) - 4;
        int g = base + ((h >>> 5) & 9) - 4;
        int b = base + (h & 9) - 4;
        return (clamp8(r) << 16) | (clamp8(g) << 8) | clamp8(b);
    }

    private static int shade(int rgb, double factor) {
        int r = clamp8((int)Math.round(((rgb >> 16) & 255) * factor));
        int g = clamp8((int)Math.round(((rgb >> 8) & 255) * factor));
        int b = clamp8((int)Math.round((rgb & 255) * factor));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int grade(int argb, double brightness, double saturation) {
        int r = (argb >> 16) & 255;
        int g = (argb >> 8) & 255;
        int b = argb & 255;
        double luma = r * 0.2126 + g * 0.7152 + b * 0.0722;
        r = clamp8((int)Math.round((luma + (r - luma) * saturation) * brightness));
        g = clamp8((int)Math.round((luma + (g - luma) * saturation) * brightness));
        b = clamp8((int)Math.round((luma + (b - luma) * saturation) * brightness));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int clamp8(int v) { return Math.max(0, Math.min(255, v)); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
}
