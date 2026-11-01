package io.github.dioramaaddons.render;

/**
 * An area of terrain seen from straight above, one entry per block column (index {@code x + z * width}).
 * Filled by the caller from whatever world data it has; shaded by {@link TopDownShader}.
 */
public final class TopDownScene {
    public static final int UNKNOWN = Integer.MIN_VALUE;

    public final int width;
    public final int depth;
    /** Y of the visible top block (the water surface where there is water), or {@link #UNKNOWN}. */
    public final int[] height;
    /** Linear RGB of the visible top (under water: of the floor), 3 floats per column. */
    public final float[] color;
    /** Water depth in blocks above the floor, 0 when dry. */
    public final int[] water;
    /** Linear RGB of the water, 3 floats per column. */
    public final float[] waterColor;
    /** Light sources such as lava: drawn at full light. */
    public final boolean[] glow;

    public TopDownScene(int width, int depth) {
        this.width = width;
        this.depth = depth;
        int n = width * depth;
        height = new int[n];
        java.util.Arrays.fill(height, UNKNOWN);
        color = new float[n * 3];
        water = new int[n];
        waterColor = new float[n * 3];
        glow = new boolean[n];
    }

    public int index(int x, int z) {
        return x + z * width;
    }

    public void set(int x, int z, int y, float r, float g, float b) {
        int i = index(x, z);
        height[i] = y;
        color[i * 3] = r;
        color[i * 3 + 1] = g;
        color[i * 3 + 2] = b;
    }

    public void setWater(int x, int z, int depth, float r, float g, float b) {
        int i = index(x, z);
        water[i] = depth;
        waterColor[i * 3] = r;
        waterColor[i * 3 + 1] = g;
        waterColor[i * 3 + 2] = b;
    }

    int heightAt(int x, int z) {
        if (x < 0 || z < 0 || x >= width || z >= depth) {
            return UNKNOWN;
        }
        return height[x + z * width];
    }
}
