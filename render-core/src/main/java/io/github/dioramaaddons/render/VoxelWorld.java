package io.github.dioramaaddons.render;

/** Blocks the oblique renderer reads. Implementations decide where the data comes from. */
public interface VoxelWorld {
    /** Unknown height, or a block in a chunk that is not available. */
    int UNKNOWN = Integer.MIN_VALUE;

    int minY();

    int maxY();

    /** Look id of the block (0 = air), or -1 when the chunk is not available. */
    int look(int x, int y, int z);

    /** Y of the highest non-air block in the column, or {@link #UNKNOWN}. */
    int surface(int x, int z);

    /** Biome tint as sRGB 0xRRGGBB for a {@link LookTable} tint kind at a column. */
    int tint(int x, int z, byte kind);
}
