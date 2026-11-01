package io.github.dioramaaddons.render;

/**
 * Tile layout of the oblique map, and the "hit map" stored next to each tile: for every pixel, which block
 * it shows. The web page reads it to place claims on the terrain and to show coordinates.
 *
 * <p>Level 0 tiles are {@link #TILE_UNITS} map units across and {@link #PIXELS} pixels (4 per block, like
 * Diorama). Level {@code k} tiles cover {@code TILE_UNITS << k} units. Hit map pixels are ARGB: alpha 255
 * when something was hit, red and green = {@code z - zRef} (16 bits), blue = {@code (y - minY) / 2}.
 */
public final class ObliqueTiles {
    public static final int TILE_UNITS = 128;
    public static final int PIXELS = 512;

    private ObliqueTiles() {}

    public static double units(int level) {
        return (double) (TILE_UNITS << level);
    }

    /** Lowest z any pixel of tile row {@code tv} can show; hit map z values are stored relative to it. */
    public static int zRef(int tv, int level, int minY, double sin, double cos) {
        return (int) Math.floor((tv * units(level) + minY * cos) / sin) - 1;
    }

    public static int encode(int z, int y, int zRef, int minY) {
        int dz = Math.max(0, Math.min(0xFFFF, z - zRef));
        int b = Math.max(0, Math.min(255, (y - minY) >> 1));
        return 0xFF000000 | dz << 8 | b;
    }

    public static int decodeZ(int pixel, int zRef) {
        return zRef + (pixel >> 8 & 0xFFFF);
    }

    public static int[] encode(ObliqueRenderer.Tile t, int zRef, int minY) {
        int[] out = new int[t.argb().length];
        for (int i = 0; i < out.length; i++) {
            if (t.hit()[i]) {
                out[i] = encode(t.hitZ()[i], t.hitY()[i], zRef, minY);
            }
        }
        return out;
    }

    /**
     * Hit map of a parent tile from its four children (NW, NE, SW, SE; null = empty): each parent pixel takes
     * one child pixel, re-based to the parent's zRef.
     */
    public static int[] downsampleHits(int[][] children, int[] childZRefs, int parentZRef, int size) {
        int[] out = new int[size * size];
        int half = size / 2;
        for (int q = 0; q < 4; q++) {
            int[] child = children[q];
            if (child == null) {
                continue;
            }
            int ox = (q & 1) * half;
            int oy = (q >> 1) * half;
            for (int y = 0; y < half; y++) {
                for (int x = 0; x < half; x++) {
                    int p = child[(2 * y) * size + 2 * x];
                    if (p >>> 24 == 0) {
                        continue;
                    }
                    int z = decodeZ(p, childZRefs[q]);
                    int dz = Math.max(0, Math.min(0xFFFF, z - parentZRef));
                    out[(oy + y) * size + ox + x] = 0xFF000000 | dz << 8 | (p & 0xFF);
                }
            }
        }
        return out;
    }
}
