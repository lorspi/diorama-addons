package io.github.dioramaaddons.webmap.render;

/**
 * Reads values from Minecraft's packed long arrays (chunk format since 1.16): each long holds
 * {@code 64 / bits} values, values never span two longs, lowest bits first.
 */
public final class PackedArray {
    private PackedArray() {}

    public static int get(long[] data, int bits, int index) {
        int perLong = 64 / bits;
        int longIndex = index / perLong;
        if (longIndex >= data.length) {
            return 0;
        }
        int shift = (index - longIndex * perLong) * bits;
        return (int) ((data[longIndex] >>> shift) & ((1L << bits) - 1));
    }

    /** Smallest {@code b} with {@code 2^b >= value}. */
    public static int ceilLog2(int value) {
        return value <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(value - 1);
    }

    /** Bits per block-state entry as written to disk. */
    public static int blockStateBits(int paletteSize) {
        return Math.max(4, ceilLog2(paletteSize));
    }

    /** Bits per heightmap entry for a world of the given height. */
    public static int heightmapBits(int worldHeight) {
        return ceilLog2(worldHeight + 1);
    }
}
