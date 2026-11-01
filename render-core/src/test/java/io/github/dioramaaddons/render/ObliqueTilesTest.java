package io.github.dioramaaddons.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ObliqueTilesTest {
    static final double SIN = Math.sin(Math.toRadians(50));
    static final double COS = Math.cos(Math.toRadians(50));

    @Test
    void hitPixelsRoundTrip() {
        int zRef = ObliqueTiles.zRef(-3, 0, -64, SIN, COS);
        int p = ObliqueTiles.encode(-120, 87, zRef, -64);
        assertEquals(0xFF, p >>> 24);
        assertEquals(-120, ObliqueTiles.decodeZ(p, zRef));
        assertEquals(87 - 1, -64 + (p & 0xFF) * 2, 1); // y is stored at half resolution
    }

    @Test
    void zRefIsBelowEveryVisibleZ() {
        for (int tv = -5; tv <= 5; tv++) {
            int zRef = ObliqueTiles.zRef(tv, 0, -64, SIN, COS);
            double v0 = tv * ObliqueTiles.units(0);
            // The lowest visible z in the tile is at its top edge, at the bottom of the world.
            double lowest = (v0 + -64 * COS) / SIN;
            assertEquals(true, zRef <= lowest);
        }
    }

    @Test
    void parentHitMapsKeepAbsoluteZ() {
        int size = 4;
        int[] childZRefs = new int[4];
        int[][] children = new int[4][];
        for (int q = 0; q < 4; q++) {
            childZRefs[q] = ObliqueTiles.zRef(q >> 1, 0, -64, SIN, COS);
            children[q] = new int[size * size];
            // z values each child tile can actually show (the southern row starts further south).
            children[q][0] = ObliqueTiles.encode(childZRefs[q] + 10 + q, 70, childZRefs[q], -64);
        }
        int parentRef = ObliqueTiles.zRef(0, 1, -64, SIN, COS);
        int[] parent = ObliqueTiles.downsampleHits(children, childZRefs, parentRef, size);
        assertEquals(childZRefs[0] + 10, ObliqueTiles.decodeZ(parent[0], parentRef));
        assertEquals(childZRefs[1] + 11, ObliqueTiles.decodeZ(parent[2], parentRef));
        assertEquals(childZRefs[3] + 13, ObliqueTiles.decodeZ(parent[2 * size + 2], parentRef));
        assertEquals(0, parent[1]);
    }
}
