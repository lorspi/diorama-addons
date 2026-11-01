package io.github.dioramaaddons.clientmap.client;

import com.mojang.serialization.Codec;
import io.github.dioramaaddons.clientmap.network.CmPayloads;
import java.util.EnumSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.jetbrains.annotations.Nullable;

/** Rebuilds a detached chunk (blocks and biomes only) from the sections the server sent. Client thread. */
final class ChunkBuilder {
    private static final Codec<PalettedContainer<BlockState>> BLOCKS = PalettedContainer.codecRW(
            Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());

    private ChunkBuilder() {}

    static @Nullable LevelChunk build(ClientLevel level, CmPayloads.ChunkEntry entry) {
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec = PalettedContainer.codecRO(biomes.asHolderIdMap(),
                biomes.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, biomes.getHolderOrThrow(Biomes.PLAINS));
        LevelChunkSection[] sections = new LevelChunkSection[level.getSectionsCount()];
        for (int i = 0; i < sections.length; i++) {
            sections[i] = new LevelChunkSection(biomes);
        }
        for (Tag t : entry.data().getList("sections", Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) t;
            int index = s.getByte("Y") - level.getMinSection();
            if (index < 0 || index >= sections.length) {
                continue;
            }
            PalettedContainer<BlockState> states = BLOCKS.parse(NbtOps.INSTANCE, s.getCompound("block_states")).result().orElse(null);
            if (states == null) {
                continue;
            }
            PalettedContainerRO<Holder<Biome>> biome = s.contains("biomes")
                    ? biomeCodec.parse(NbtOps.INSTANCE, s.getCompound("biomes")).result().orElse(sections[index].getBiomes())
                    : sections[index].getBiomes();
            sections[index] = new LevelChunkSection(states, biome);
        }
        LevelChunk chunk = new LevelChunk(level, new ChunkPos(entry.x(), entry.z()), UpgradeData.EMPTY, new LevelChunkTicks<>(),
                new LevelChunkTicks<>(), 0L, sections, null, null);
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.WORLD_SURFACE, Heightmap.Types.MOTION_BLOCKING));
        return chunk;
    }
}
