package io.github.dioramaaddons.webmap.render;

/** Builds a zoomed-out tile from its four children by averaging 2×2 pixel blocks. */
public final class Downsampler {
    private Downsampler() {}

    /**
     * @param children north-west, north-east, south-west, south-east tiles (ARGB, size×size), null = empty
     * @return the parent tile, size×size
     */
    public static int[] combine(int[][] children, int size) {
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
                    out[(oy + y) * size + ox + x] = average(child[(2 * y) * size + 2 * x],
                            child[(2 * y) * size + 2 * x + 1], child[(2 * y + 1) * size + 2 * x],
                            child[(2 * y + 1) * size + 2 * x + 1]);
                }
            }
        }
        return out;
    }

    /** Alpha-weighted average of four ARGB pixels. */
    static int average(int... px) {
        int a = 0, r = 0, g = 0, b = 0;
        for (int p : px) {
            int pa = p >>> 24;
            a += pa;
            r += ((p >> 16) & 0xFF) * pa;
            g += ((p >> 8) & 0xFF) * pa;
            b += (p & 0xFF) * pa;
        }
        if (a == 0) {
            return 0;
        }
        return ((a / 4) << 24) | ((r / a) << 16) | ((g / a) << 8) | (b / a);
    }
}
