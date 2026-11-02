package io.github.dioramaaddons.webmap.render;

import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Finds loaded chunks whose surface changed (or that were just generated) and hands a snapshot of their blocks
 * to the renderer, so the map follows the game without waiting for the next autosave (every five minutes) or
 * for the chunk to unload.
 *
 * <p>Every {@code intervalTicks}, loaded chunks are checked in small slices on the server thread, within a
 * time budget per tick: a fingerprint of the top block of each column is compared with the last one seen.
 * Only changed chunks are copied (block and biome palettes plus the surface heightmap, the same format as the
 * saved chunk, which is what the renderers read).
 */
public final class LiveChunks {
    /** Fingerprint value meaning "never rendered: snapshot it on the next pass". */
    private static final long NEW = Long.MIN_VALUE;
    /** Fingerprint value meaning "loaded from disk as it was rendered: just remember the fingerprint". */
    private static final long BASELINE = Long.MIN_VALUE + 1;

    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATES = PalettedContainer.codecRW(
            Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES,
            Blocks.AIR.defaultBlockState());

    public interface Sink {
        /** A chunk changed; {@code tag} is its snapshot. Server thread. */
        void changed(String dim, int chunkX, int chunkZ, CompoundTag tag);
    }

    private final Map<String, ServerLevel> levels;
    private final LiveChunkStore store;
    private final Sink sink;
    private final Map<String, Map<Long, Long>> tracked = new ConcurrentHashMap<>();
    private final Map<String, Codec<PalettedContainerRO<Holder<Biome>>>> biomeCodecs = new ConcurrentHashMap<>();
    private final Deque<long[]> pass = new ArrayDeque<>();
    private final String[] dimIds;
    private long lastPassTick = Long.MIN_VALUE / 2;
    private long snapshots;

    public LiveChunks(Map<String, ServerLevel> levels, LiveChunkStore store, Sink sink) {
        this.levels = levels;
        this.store = store;
        this.sink = sink;
        this.dimIds = levels.keySet().toArray(String[]::new);
    }

    /** A full chunk was loaded (any thread). {@code generated}: it was just generated or promoted to full. */
    public void loaded(String dim, int chunkX, int chunkZ, boolean generated) {
        if (!levels.containsKey(dim)) {
            return;
        }
        tracked.computeIfAbsent(dim, d -> new ConcurrentHashMap<>())
                .put(ChunkPos.asLong(chunkX, chunkZ), generated ? NEW : BASELINE);
    }

    /** A chunk was unloaded (any thread): it is saved first, so the saved copy is current. */
    public void unloaded(String dim, int chunkX, int chunkZ) {
        Map<Long, Long> m = tracked.get(dim);
        if (m != null) {
            m.remove(ChunkPos.asLong(chunkX, chunkZ));
        }
        store.remove(dim, chunkX, chunkZ);
    }

    /** A chunk was written to disk (any thread). */
    public void saved(String dim, int chunkX, int chunkZ) {
        store.remove(dim, chunkX, chunkZ);
    }

    public long snapshots() {
        return snapshots;
    }

    /**
     * Server thread, every tick.
     *
     * @param intervalTicks ticks between the start of two passes over the loaded chunks; 0 = off
     * @param budgetNanos   time the checks may take per tick
     */
    public void tick(long tickCount, int intervalTicks, long budgetNanos) {
        if (intervalTicks <= 0) {
            pass.clear();
            return;
        }
        if (pass.isEmpty()) {
            if (tickCount - lastPassTick < intervalTicks) {
                return;
            }
            lastPassTick = tickCount;
            for (int d = 0; d < dimIds.length; d++) {
                Map<Long, Long> m = tracked.get(dimIds[d]);
                if (m != null) {
                    for (long pos : m.keySet()) {
                        pass.add(new long[] {d, pos});
                    }
                }
            }
        }
        long deadline = System.nanoTime() + budgetNanos;
        while (!pass.isEmpty() && System.nanoTime() < deadline) {
            long[] next = pass.poll();
            check(dimIds[(int) next[0]], next[1]);
        }
    }

    private void check(String dim, long pos) {
        Map<Long, Long> m = tracked.get(dim);
        Long last = m == null ? null : m.get(pos);
        ServerLevel level = levels.get(dim);
        if (last == null || level == null) {
            return;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(pos), ChunkPos.getZ(pos));
        if (chunk == null) {
            m.remove(pos); // unloaded without an event we saw
            return;
        }
        long fp = fingerprint(chunk);
        if (fp == last) {
            return;
        }
        m.put(pos, fp);
        if (last == BASELINE) {
            return;
        }
        CompoundTag tag = snapshot(level, chunk);
        if (tag == null) {
            return;
        }
        snapshots++;
        store.put(dim, chunk.getPos().x, chunk.getPos().z, tag);
        sink.changed(dim, chunk.getPos().x, chunk.getPos().z, tag);
    }

    /** Height and block state of the top of every column. */
    static long fingerprint(LevelChunk chunk) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        long h = 1125899906842597L;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                int state = Block.getId(chunk.getBlockState(p.set(x0 + x, y, z0 + z)));
                h = 31 * h + y;
                h = 31 * h + state;
            }
        }
        // Never collide with the markers.
        return h == NEW || h == BASELINE ? h + 2 : h;
    }

    /** The parts of a saved chunk the renderers read: status, surface heightmap, block and biome palettes. */
    private @Nullable CompoundTag snapshot(ServerLevel level, LevelChunk chunk) {
        Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec = biomeCodecs.computeIfAbsent(
                level.dimension().location().toString(), d -> biomeCodec(level));
        CompoundTag t = new CompoundTag();
        t.putString("Status", "minecraft:full");
        CompoundTag heightmaps = new CompoundTag();
        heightmaps.putLongArray("WORLD_SURFACE",
                chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE).getRawData().clone());
        t.put("Heightmaps", heightmaps);
        ListTag sections = new ListTag();
        LevelChunkSection[] all = chunk.getSections();
        try {
            for (int i = 0; i < all.length; i++) {
                LevelChunkSection s = all[i];
                if (s == null || s.hasOnlyAir()) {
                    continue;
                }
                CompoundTag st = new CompoundTag();
                st.putByte("Y", (byte) chunk.getSectionYFromSectionIndex(i));
                st.put("block_states", BLOCK_STATES.encodeStart(NbtOps.INSTANCE, s.getStates()).getOrThrow());
                st.put("biomes", biomeCodec.encodeStart(NbtOps.INSTANCE, s.getBiomes()).getOrThrow());
                sections.add(st);
            }
        } catch (RuntimeException e) {
            return null;
        }
        t.put("sections", sections);
        return t;
    }

    private static Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec(ServerLevel level) {
        Registry<Biome> registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        return PalettedContainer.codecRO(registry.asHolderIdMap(), registry.holderByNameCodec(),
                PalettedContainer.Strategy.SECTION_BIOMES, registry.getHolderOrThrow(Biomes.PLAINS));
    }
}
