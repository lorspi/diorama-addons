package io.github.dioramaaddons.render;

import java.util.Arrays;

/**
 * How each kind of block looks and behaves for the oblique renderer, indexed by a small id. Id 0 is air.
 * Colours are linear RGB of the block's top and side textures before biome tint.
 */
public final class LookTable {
    public static final byte AIR = 0;
    /** Stops rays and casts shadows. */
    public static final byte SOLID = 1;
    /** Plants, flowers, rails: partly coloured in, rays continue. */
    public static final byte PARTIAL = 2;
    public static final byte WATER = 3;
    /** Solid and gives light (lava, glowstone...): drawn bright. */
    public static final byte GLOW = 4;

    /** Biome tints, resolved per column by the world. */
    public static final byte TINT_NONE = 0;
    public static final byte TINT_GRASS = 1;
    public static final byte TINT_FOLIAGE = 2;
    public static final byte TINT_WATER = 3;

    private byte[] kind = new byte[64];
    private byte[] tint = new byte[64];
    private boolean[] sideTinted = new boolean[64];
    private float[] coverage = new float[64];
    private float[] top = new float[64 * 3];
    private float[] side = new float[64 * 3];
    private int size = 1; // id 0 = air

    public synchronized int add(byte kind, byte tint, float coverage, float[] top, float[] side) {
        return add(kind, tint, true, coverage, top, side);
    }

    /** @param sideTinted whether the biome tint also applies to the side face (not for grass blocks) */
    public synchronized int add(byte kind, byte tint, boolean sideTinted, float coverage, float[] top, float[] side) {
        if (size == this.kind.length) {
            int n = size * 2;
            this.kind = Arrays.copyOf(this.kind, n);
            this.tint = Arrays.copyOf(this.tint, n);
            this.sideTinted = Arrays.copyOf(this.sideTinted, n);
            this.coverage = Arrays.copyOf(this.coverage, n);
            this.top = Arrays.copyOf(this.top, n * 3);
            this.side = Arrays.copyOf(this.side, n * 3);
        }
        int id = size++;
        this.kind[id] = kind;
        this.tint[id] = tint;
        this.sideTinted[id] = sideTinted;
        this.coverage[id] = coverage;
        System.arraycopy(top, 0, this.top, id * 3, 3);
        System.arraycopy(side, 0, this.side, id * 3, 3);
        return id;
    }

    public byte kind(int id) {
        return kind[id];
    }

    public byte tint(int id) {
        return tint[id];
    }

    public boolean sideTinted(int id) {
        return sideTinted[id];
    }

    public float coverage(int id) {
        return coverage[id];
    }

    float top(int id, int channel) {
        return top[id * 3 + channel];
    }

    float side(int id, int channel) {
        return side[id * 3 + channel];
    }

    /** Stops rays and shadows. */
    public boolean opaque(int id) {
        byte k = kind[id];
        return k == SOLID || k == GLOW;
    }
}
