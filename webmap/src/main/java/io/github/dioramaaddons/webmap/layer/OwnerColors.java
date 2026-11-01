package io.github.dioramaaddons.webmap.layer;

import java.util.UUID;

/** Stable, distinct colour per owner: hue from the UUID hash, fixed saturation and lightness. */
public final class OwnerColors {
    private OwnerColors() {}

    public static String hex(UUID owner) {
        int h = owner.hashCode();
        h ^= h >>> 16;
        float hue = (Math.floorMod(h * 0x9E3779B1, 360)) / 360f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 0.75f, 0.95f) & 0xFFFFFF;
        return String.format("#%06x", rgb);
    }
}
