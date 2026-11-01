package io.github.dioramaaddons.claims.data;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import io.github.dioramaaddons.claims.core.ClaimStore;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/** Stores every claim in {@code <world>/data/dioaddons_claims.dat}. */
public final class ClaimSavedData extends SavedData {
    public static final String NAME = "dioaddons_claims";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final SavedData.Factory<ClaimSavedData> FACTORY =
            new SavedData.Factory<>(ClaimSavedData::new, ClaimSavedData::load);

    private final ClaimStore store = new ClaimStore();

    public ClaimSavedData() {
        store.addListener((type, claim) -> setDirty());
    }

    public ClaimStore store() {
        return store;
    }

    private static ClaimSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ClaimSavedData data = new ClaimSavedData();
        ClaimCodecs.decode(new Dynamic<>(NbtOps.INSTANCE, tag), data.store)
                .resultOrPartial(err -> LOGGER.error("Problem reading claims data: {}", err));
        LOGGER.info("Loaded {} claims", data.store.all().size());
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Tag encoded = ClaimCodecs.encode(store, NbtOps.INSTANCE)
                .resultOrPartial(err -> LOGGER.error("Problem saving claims data: {}", err))
                .orElse(null);
        if (encoded instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                tag.put(key, compound.get(key));
            }
        }
        return tag;
    }
}
