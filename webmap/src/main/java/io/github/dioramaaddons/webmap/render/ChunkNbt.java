package io.github.dioramaaddons.webmap.render;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/** Converts saved chunk NBT into {@link ChunkData}. Only fully generated chunks are returned. */
public final class ChunkNbt {
    private ChunkNbt() {}

    public static @Nullable ChunkData parse(CompoundTag tag, int minY, int worldHeight) {
        String status = tag.getString("Status");
        if (!status.equals("minecraft:full") && !status.equals("full")) {
            return null;
        }
        long[] heightmap = null;
        CompoundTag heightmaps = tag.getCompound("Heightmaps");
        if (heightmaps.contains("WORLD_SURFACE", Tag.TAG_LONG_ARRAY)) {
            heightmap = heightmaps.getLongArray("WORLD_SURFACE");
        }
        Map<Integer, ChunkData.Section> sections = new HashMap<>();
        ListTag list = tag.getList("sections", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag section = list.getCompound(i);
            if (!section.contains("block_states", Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag states = section.getCompound("block_states");
            ListTag palette = states.getList("palette", Tag.TAG_COMPOUND);
            ChunkData.Entry[] entries = new ChunkData.Entry[palette.size()];
            for (int p = 0; p < palette.size(); p++) {
                CompoundTag e = palette.getCompound(p);
                boolean waterlogged = e.getCompound("Properties").getString("waterlogged").equals("true");
                entries[p] = new ChunkData.Entry(e.getString("Name"), waterlogged);
            }
            long[] data = states.contains("data", Tag.TAG_LONG_ARRAY) ? states.getLongArray("data") : null;
            sections.put((int) section.getByte("Y"), new ChunkData.Section(entries, data));
        }
        return new ChunkData(minY, worldHeight, heightmap, sections);
    }
}
