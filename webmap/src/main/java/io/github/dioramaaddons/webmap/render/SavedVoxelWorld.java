package io.github.dioramaaddons.webmap.render;

import io.github.dioramaaddons.render.LookTable;
import io.github.dioramaaddons.render.VoxelWorld;
import io.github.dioramaaddons.webmap.assets.BiomeTints;
import io.github.dioramaaddons.webmap.assets.ServerBlockLooks;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Blocks of one dimension read from the saved world through the server's chunk storage (asynchronous, never
 * the server thread), with a cache of recently used chunks. Render thread only.
 */
final class SavedVoxelWorld implements VoxelWorld {
    private static final int CACHE = 2048;

    private record Section(int[] ids, long[] data, int bits, String[] biomes, long[] biomeData, int biomeBits) {}

    private static final class Chunk {
        final int[] surface = new int[256];
        final Map<Integer, Section> sections = new HashMap<>();
    }

    private static final Chunk MISSING = new Chunk();

    private final ServerLevel level;
    private final ServerBlockLooks looks;
    private final BiomeTints tints;
    private final Runnable beforeRead;
    private final LiveChunkStore live;
    private final String dim;
    private final int minY;
    private final int maxY;
    private final Map<Long, Chunk> cache = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Chunk> eldest) {
            return size() > CACHE;
        }
    };

    SavedVoxelWorld(ServerLevel level, ServerBlockLooks looks, BiomeTints tints, LiveChunkStore live, Runnable beforeRead) {
        this.level = level;
        this.live = live;
        this.dim = level.dimension().location().toString();
        this.looks = looks;
        this.tints = tints;
        this.beforeRead = beforeRead;
        this.minY = level.getMinBuildHeight();
        this.maxY = level.getMaxBuildHeight() - 1;
    }

    LookTable table() {
        return looks.table();
    }

    /** Forgets cached chunks, after they changed. */
    void invalidate(int chunkX, int chunkZ) {
        cache.remove(ChunkPos.asLong(chunkX, chunkZ));
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int maxY() {
        return maxY;
    }

    private Chunk chunk(int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        Chunk c = cache.get(key);
        if (c == null) {
            c = load(cx, cz);
            cache.put(key, c);
        }
        return c;
    }

    /** Highest surface in a chunk, or {@link #UNKNOWN} if it is not generated. */
    int chunkTop(int cx, int cz) {
        Chunk c = chunk(cx, cz);
        if (c == MISSING) {
            return UNKNOWN;
        }
        int top = UNKNOWN;
        for (int s : c.surface) {
            top = Math.max(top, s);
        }
        return top;
    }

    private Chunk load(int cx, int cz) {
        beforeRead.run();
        Optional<CompoundTag> tag;
        CompoundTag snapshot = live.get(dim, cx, cz);
        if (snapshot != null) {
            // Loaded and changed since it was saved: newer than the copy on disk.
            tag = Optional.of(snapshot);
        } else {
            try {
                tag = level.getChunkSource().chunkMap.read(new ChunkPos(cx, cz)).get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                return MISSING;
            }
        }
        if (tag.isEmpty()) {
            return MISSING;
        }
        CompoundTag t = tag.get();
        String status = t.getString("Status");
        if (!status.equals("minecraft:full") && !status.equals("full")) {
            return MISSING;
        }
        Chunk c = new Chunk();
        long[] hm = t.getCompound("Heightmaps").getLongArray("WORLD_SURFACE");
        int bits = PackedArray.heightmapBits(level.getHeight());
        for (int i = 0; i < 256; i++) {
            int h = hm.length == 0 ? 0 : PackedArray.get(hm, bits, i);
            c.surface[i] = h == 0 ? minY - 1 : minY + h - 1;
        }
        ListTag sections = t.getList("sections", Tag.TAG_COMPOUND);
        for (int i = 0; i < sections.size(); i++) {
            CompoundTag s = sections.getCompound(i);
            if (!s.contains("block_states", Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag states = s.getCompound("block_states");
            ListTag palette = states.getList("palette", Tag.TAG_COMPOUND);
            int[] ids = new int[palette.size()];
            for (int p = 0; p < ids.length; p++) {
                CompoundTag e = palette.getCompound(p);
                boolean waterlogged = e.getCompound("Properties").getString("waterlogged").equals("true");
                ids[p] = looks.id(e.getString("Name"), waterlogged);
            }
            long[] data = states.contains("data", Tag.TAG_LONG_ARRAY) ? states.getLongArray("data") : null;
            CompoundTag biomes = s.getCompound("biomes");
            ListTag bp = biomes.getList("palette", Tag.TAG_STRING);
            String[] biomeNames = new String[bp.size()];
            for (int p = 0; p < biomeNames.length; p++) {
                biomeNames[p] = bp.getString(p);
            }
            long[] biomeData = biomes.contains("data", Tag.TAG_LONG_ARRAY) ? biomes.getLongArray("data") : null;
            c.sections.put((int) s.getByte("Y"), new Section(ids, data, PackedArray.blockStateBits(ids.length), biomeNames,
                    biomeData, PackedArray.ceilLog2(biomeNames.length)));
        }
        return c;
    }

    @Override
    public int look(int x, int y, int z) {
        Chunk c = chunk(x >> 4, z >> 4);
        if (c == MISSING) {
            return -1;
        }
        Section s = c.sections.get(y >> 4);
        if (s == null || s.ids.length == 0) {
            return 0;
        }
        if (s.data == null || s.ids.length == 1) {
            return s.ids[0];
        }
        int idx = PackedArray.get(s.data, s.bits, ((y & 15) << 8) | ((z & 15) << 4) | (x & 15));
        return idx < s.ids.length ? s.ids[idx] : 0;
    }

    @Override
    public int surface(int x, int z) {
        Chunk c = chunk(x >> 4, z >> 4);
        return c == MISSING ? UNKNOWN : c.surface[((z & 15) << 4) | (x & 15)];
    }

    @Override
    public int tint(int x, int z, byte kind) {
        if (kind == LookTable.TINT_NONE) {
            return -1;
        }
        Chunk c = chunk(x >> 4, z >> 4);
        if (c == MISSING) {
            return -1;
        }
        int y = c.surface[((z & 15) << 4) | (x & 15)];
        Section s = c.sections.get(y >> 4);
        if (s == null) {
            s = c.sections.get((y - 1) >> 4);
        }
        String biome = "minecraft:plains";
        if (s != null && s.biomes.length > 0) {
            int bi = 0;
            if (s.biomeData != null && s.biomes.length > 1 && s.biomeBits > 0) {
                bi = PackedArray.get(s.biomeData, s.biomeBits, ((y & 15) >> 2) << 4 | ((z & 15) >> 2) << 2 | ((x & 15) >> 2));
            }
            biome = s.biomes[Math.min(bi, s.biomes.length - 1)];
        }
        return tints.of(biome)[kind - 1];
    }
}
