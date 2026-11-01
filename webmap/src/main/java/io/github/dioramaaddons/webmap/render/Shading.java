package io.github.dioramaaddons.webmap.render;

/** Vanilla-map style brightness levels and relief shading. */
public final class Shading {
    public static final int LOW = 180;
    public static final int NORMAL = 220;
    public static final int HIGH = 255;

    private Shading() {}

    /** Relief: compares a column with the one north of it, with a light checker dither like vanilla maps. */
    public static int relief(int height, int northHeight, int x, int z) {
        double d = (height - northHeight) * 0.8 + (((x + z) & 1) - 0.5) * 0.4;
        return d > 0.6 ? HIGH : d < -0.6 ? LOW : NORMAL;
    }

    /** Water: deeper water is darker. */
    public static int water(int depth, int x, int z) {
        double d = depth * 0.1 + ((x + z) & 1) * 0.2;
        return d < 0.5 ? HIGH : d > 0.9 ? LOW : NORMAL;
    }

    /** Applies a brightness level to an RGB colour and returns opaque ARGB. */
    public static int apply(int rgb, int brightness) {
        int r = ((rgb >> 16) & 0xFF) * brightness / 255;
        int g = ((rgb >> 8) & 0xFF) * brightness / 255;
        int b = (rgb & 0xFF) * brightness / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
