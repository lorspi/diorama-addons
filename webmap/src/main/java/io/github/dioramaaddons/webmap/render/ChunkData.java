package io.github.dioramaaddons.webmap.render;

import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Minimal, Minecraft-independent view of a saved chunk: block palettes per section and the WORLD_SURFACE
 * heightmap.
 *
 * @param minY        lowest block Y of the dimension
 * @param worldHeight total height of the dimension
 * @param heightmap   packed WORLD_SURFACE heightmap (256 entries, x + z * 16), or null
 * @param sections    section Y index (block Y >> 4) → section
 */
public record ChunkData(int minY, int worldHeight, long @Nullable [] heightmap, Map<Integer, Section> sections) {

    /** A palette entry. {@code waterlogged} is true when the block state has waterlogged=true. */
    public record Entry(String name, boolean waterlogged) {}

    /** {@code data} is null when the palette has a single entry. */
    public record Section(Entry[] palette, long @Nullable [] data) {
        Entry get(int x, int y, int z) {
            if (palette.length == 0) {
                return null;
            }
            if (data == null || palette.length == 1) {
                return palette[0];
            }
            int idx = PackedArray.get(data, PackedArray.blockStateBits(palette.length), (y << 8) | (z << 4) | x);
            return idx < palette.length ? palette[idx] : null;
        }
    }

    /** Y of the highest non-air block in the column, or {@link Integer#MIN_VALUE} if the column is empty. */
    public int surfaceY(int x, int z) {
        if (heightmap == null) {
            return Integer.MIN_VALUE;
        }
        int h = PackedArray.get(heightmap, PackedArray.heightmapBits(worldHeight), x + z * 16);
        return h == 0 ? Integer.MIN_VALUE : minY + h - 1;
    }

    public @Nullable Entry block(int x, int y, int z) {
        Section s = sections.get(y >> 4);
        return s == null ? null : s.get(x, y & 15, z);
    }
}
