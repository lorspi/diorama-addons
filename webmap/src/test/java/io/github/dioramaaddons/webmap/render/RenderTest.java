package io.github.dioramaaddons.webmap.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RenderTest {
    static final int STONE = 0x707070;
    static final int GRASS = 0x7FB238;
    static final int WATER = 0x4040FF;

    static final BlockColorSource COLORS = new BlockColorSource() {
        @Override
        public int color(String id) {
            return switch (id) {
                case "minecraft:stone" -> STONE;
                case "minecraft:grass_block" -> GRASS;
                default -> -1;
            };
        }

        @Override
        public boolean isWater(String id) {
            return id.equals("minecraft:water");
        }

        @Override
        public int waterColor() {
            return WATER;
        }
    };

    /** Packs values like Minecraft's SimpleBitStorage. */
    static long[] pack(int[] values, int bits) {
        int perLong = 64 / bits;
        long[] out = new long[(values.length + perLong - 1) / perLong];
        for (int i = 0; i < values.length; i++) {
            out[i / perLong] |= ((long) values[i]) << ((i % perLong) * bits);
        }
        return out;
    }

    @Test
    void packedArrayReadsWhatWasPacked() {
        int[] values = new int[256];
        for (int i = 0; i < values.length; i++) {
            values[i] = (i * 37) % 385;
        }
        long[] packed = pack(values, 9);
        assertEquals(37, packed.length);
        for (int i = 0; i < values.length; i++) {
            assertEquals(values[i], PackedArray.get(packed, 9, i));
        }
    }

    @Test
    void bits() {
        assertEquals(4, PackedArray.blockStateBits(2));
        assertEquals(5, PackedArray.blockStateBits(17));
        assertEquals(9, PackedArray.heightmapBits(384));
        assertEquals(8, PackedArray.heightmapBits(256 - 1));
    }

    /** Flat chunk: stone below y, grass at {@code surface}, world from y=-64, height 384. */
    static ChunkData flat(int surface, boolean waterOnTop) {
        int minY = -64;
        int height = 384;
        int[] hm = new int[256];
        int top = waterOnTop ? surface + 2 : surface;
        java.util.Arrays.fill(hm, top - minY + 1);
        ChunkData.Entry[] palette = {
            new ChunkData.Entry("minecraft:air", false), new ChunkData.Entry("minecraft:stone", false),
            new ChunkData.Entry("minecraft:grass_block", false), new ChunkData.Entry("minecraft:water", false)
        };
        int sectionY = surface >> 4;
        int[] blocks = new int[4096];
        for (int y = 0; y < 16; y++) {
            int worldY = sectionY * 16 + y;
            int v = worldY < surface ? 1 : worldY == surface ? 2 : (waterOnTop && worldY <= surface + 2) ? 3 : 0;
            for (int i = 0; i < 256; i++) {
                blocks[(y << 8) | i] = v;
            }
        }
        return new ChunkData(minY, height, pack(hm, PackedArray.heightmapBits(height)),
                Map.of(sectionY, new ChunkData.Section(palette, pack(blocks, 4))));
    }

    @Test
    void flatGrassIsGreen() {
        int[] px = ChunkRenderer.render(flat(4, false), null, COLORS);
        int center = px[8 + 8 * 16];
        assertEquals(0xFF, center >>> 24);
        // Flat ground: NORMAL or dithered brightness of the grass colour, never another colour.
        int r = (center >> 16) & 0xFF;
        assertEquals(true, r == (0x7F * Shading.NORMAL / 255) || r == (0x7F * Shading.HIGH / 255)
                || r == (0x7F * Shading.LOW / 255));
    }

    @Test
    void waterIsDetectedWithDepth() {
        int[] px = ChunkRenderer.render(flat(4, true), null, COLORS);
        int p = px[3 + 5 * 16];
        assertEquals(0xFF, p & 0xFF, "blue channel of shallow water should be bright");
    }

    @Test
    void emptyColumnIsTransparent() {
        ChunkData empty = new ChunkData(-64, 384, null, Map.of());
        int[] px = ChunkRenderer.render(empty, null, COLORS);
        for (int p : px) {
            assertEquals(0, p);
        }
    }

    @Test
    void reliefMakesSlopesBrighterOrDarker() {
        assertEquals(Shading.HIGH, Shading.relief(70, 60, 0, 0));
        assertEquals(Shading.LOW, Shading.relief(60, 70, 0, 0));
        assertNotEquals(Shading.apply(GRASS, Shading.HIGH), Shading.apply(GRASS, Shading.LOW));
    }

    @Test
    void tileMath() {
        assertEquals(0, TileMath.tileOf(511, 0));
        assertEquals(-1, TileMath.tileOf(-1, 0));
        assertEquals(1, TileMath.tileOf(1024, 1));
        assertEquals(-1, TileMath.regionOfChunk(-1));
        assertEquals(-1, TileMath.parent(-1));
        assertEquals(-1, TileMath.parent(-2));
        assertEquals(0, TileMath.parent(1));
    }

    @Test
    void downsampleAveragesAndKeepsTransparency() {
        int size = 4;
        int[] nw = new int[size * size];
        java.util.Arrays.fill(nw, 0xFF000000);
        nw[0] = 0xFFFFFFFF; // one white pixel in the first 2x2 block
        int[] out = Downsampler.combine(new int[][] {nw, null, null, null}, size);
        assertEquals(0xFF3F3F3F, out[0]);
        assertEquals(0xFF000000, out[1]);
        assertEquals(0, out[2]); // north-east quadrant is empty
        assertEquals(0x7F7F7F7F, Downsampler.average(0xFF000000, 0xFFFFFFFF, 0, 0));
    }
}
