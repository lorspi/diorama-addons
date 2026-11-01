package io.github.dioramaaddons.webmap.render;

import org.jetbrains.annotations.Nullable;

/** Renders one chunk to 16×16 ARGB pixels (index x + z * 16), top-down with relief shading. */
public final class ChunkRenderer {
    /** How far below the surface we look for a visible block (glass roofs, barriers...). */
    static final int MAX_DESCENT = 64;
    static final int MAX_WATER_DEPTH = 32;

    private ChunkRenderer() {}

    private record Column(int y, int argbBase, int waterDepth) {}

    public static int[] render(ChunkData chunk, @Nullable ChunkData north, BlockColorSource colors) {
        int[] heights = new int[16 * 16];
        int[] pixels = new int[16 * 16];
        Column[] columns = new Column[256];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                Column c = column(chunk, x, z, colors);
                columns[x + z * 16] = c;
                heights[x + z * 16] = c == null ? Integer.MIN_VALUE : c.y;
            }
        }
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                Column c = columns[x + z * 16];
                if (c == null) {
                    pixels[x + z * 16] = 0;
                    continue;
                }
                if (c.waterDepth > 0) {
                    pixels[x + z * 16] = Shading.apply(c.argbBase, Shading.water(c.waterDepth, x, z));
                    continue;
                }
                int northHeight;
                if (z > 0) {
                    northHeight = heights[x + (z - 1) * 16];
                } else {
                    Column n = north == null ? null : column(north, x, 15, colors);
                    northHeight = n == null ? c.y : n.y;
                }
                if (northHeight == Integer.MIN_VALUE) {
                    northHeight = c.y;
                }
                pixels[x + z * 16] = Shading.apply(c.argbBase, Shading.relief(c.y, northHeight, x, z));
            }
        }
        return pixels;
    }

    private static @Nullable Column column(ChunkData chunk, int x, int z, BlockColorSource colors) {
        int top = chunk.surfaceY(x, z);
        if (top == Integer.MIN_VALUE) {
            return null;
        }
        int bottom = Math.max(chunk.minY(), top - MAX_DESCENT);
        for (int y = top; y >= bottom; y--) {
            ChunkData.Entry e = chunk.block(x, y, z);
            if (e == null) {
                continue;
            }
            if (e.waterlogged() || colors.isWater(e.name())) {
                int depth = 1;
                while (depth < MAX_WATER_DEPTH && y - depth >= chunk.minY()) {
                    ChunkData.Entry below = chunk.block(x, y - depth, z);
                    if (below == null || !(below.waterlogged() || colors.isWater(below.name()))) {
                        break;
                    }
                    depth++;
                }
                return new Column(y, colors.waterColor(), depth);
            }
            int color = colors.color(e.name());
            if (color >= 0) {
                return new Column(y, color, 0);
            }
        }
        return null;
    }
}
