package io.github.dioramaaddons.claimsapi;

/** Chunk column coordinates. Packed form matches Minecraft's {@code ChunkPos.toLong()}. */
public record ChunkCoord(int x, int z) {
    public long toLong() {
        return pack(x, z);
    }

    public static long pack(int x, int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
    }

    public static ChunkCoord fromLong(long packed) {
        return new ChunkCoord(unpackX(packed), unpackZ(packed));
    }

    public static int unpackX(long packed) {
        return (int) (packed & 0xFFFFFFFFL);
    }

    public static int unpackZ(long packed) {
        return (int) (packed >>> 32 & 0xFFFFFFFFL);
    }
}
