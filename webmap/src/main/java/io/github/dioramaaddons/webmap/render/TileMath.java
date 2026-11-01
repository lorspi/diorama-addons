package io.github.dioramaaddons.webmap.render;

/**
 * Tile layout. Zoom level 0 is 1 pixel per block with 512×512 tiles, so one tile equals one region file
 * (32×32 chunks). Level {@code k > 0} is zoomed out: a tile covers {@code 512 · 2^k} blocks.
 */
public final class TileMath {
    public static final int TILE_SIZE = 512;

    private TileMath() {}

    public static int blocksPerTile(int zoomOut) {
        return TILE_SIZE << zoomOut;
    }

    /** Tile index containing a block coordinate. */
    public static int tileOf(int block, int zoomOut) {
        return Math.floorDiv(block, blocksPerTile(zoomOut));
    }

    public static int regionOfChunk(int chunk) {
        return chunk >> 5;
    }

    /** Parent tile index one level further out. */
    public static int parent(int tile) {
        return Math.floorDiv(tile, 2);
    }
}
