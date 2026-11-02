package io.github.dioramaaddons.webmap.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

/**
 * Snapshots of loaded chunks that changed since they were last written to disk. The server thread puts them
 * here (see {@code LiveChunks}); renderers read them before the saved copy, so the map shows newly explored
 * or edited terrain without waiting for the next autosave. A snapshot is dropped once the chunk is saved or
 * unloaded, because the saved copy is then at least as new. Thread-safe.
 */
public final class LiveChunkStore {
    private final Map<String, Map<Long, CompoundTag>> chunks = new ConcurrentHashMap<>();

    public void put(String dim, int chunkX, int chunkZ, CompoundTag tag) {
        chunks.computeIfAbsent(dim, d -> new ConcurrentHashMap<>()).put(ChunkPos.asLong(chunkX, chunkZ), tag);
    }

    public void remove(String dim, int chunkX, int chunkZ) {
        Map<Long, CompoundTag> m = chunks.get(dim);
        if (m != null) {
            m.remove(ChunkPos.asLong(chunkX, chunkZ));
        }
    }

    public @Nullable CompoundTag get(String dim, int chunkX, int chunkZ) {
        Map<Long, CompoundTag> m = chunks.get(dim);
        return m == null ? null : m.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    public int size() {
        int n = 0;
        for (Map<Long, CompoundTag> m : chunks.values()) {
            n += m.size();
        }
        return n;
    }

    public void clear() {
        chunks.clear();
    }
}
