package io.github.dioramaaddons.claimsapi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChunkCoordTest {
    @Test
    void packRoundTripsNegativeAndPositive() {
        int[][] samples = {{0, 0}, {-1, -1}, {123456, -98765}, {Integer.MIN_VALUE, Integer.MAX_VALUE}};
        for (int[] s : samples) {
            ChunkCoord c = new ChunkCoord(s[0], s[1]);
            assertEquals(c, ChunkCoord.fromLong(c.toLong()));
        }
    }

    @Test
    void packMatchesMinecraftLayout() {
        // ChunkPos.asLong(x, z) = (long)x & 4294967295L | ((long)z & 4294967295L) << 32
        assertEquals(0x00000002_00000001L, ChunkCoord.pack(1, 2));
    }
}
