package io.github.dioramaaddons.clientmap.compat.diorama;

import java.util.Map;
import java.util.Set;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;

/**
 * Decides whether the Diorama integration runs. It only does with Diorama versions whose internals were
 * checked (see docs/clientmap-research.md). This class never references Diorama itself.
 */
public final class DioramaCompat {
    public static final String MOD_ID = "diorama";
    /**
     * Diorama versions the mixins and calls were checked against, each with the shape of its internals that
     * differ between them (see docs/clientmap-research.md).
     */
    private static final Map<String, Internals> VERSIONS = Map.of(
            "0.6.0+neoforge-1.21.1", Internals.V0_6,
            "0.6.1+neoforge-1.21.1", Internals.V0_6,
            "0.7.0+neoforge-1.21.1", Internals.V0_7);
    /** Diorama versions the mixins and calls were written and tested against. */
    public static final Set<String> SUPPORTED = VERSIONS.keySet();

    /** What changed in the Diorama internals we hook. */
    enum Internals {
        /** {@code MapScreen.spotItems(MapLevel.Spot)}, private {@code dimension()}. */
        V0_6,
        /** {@code MapScreen.spotItems(MapLevel.Spot, BlockPos)}, public {@code dimension()}. */
        V0_7
    }

    private DioramaCompat() {}

    /** Usable while mods are still loading (mixin plugin). */
    static String loadingVersion() {
        LoadingModList list = LoadingModList.get();
        ModFileInfo file = list == null ? null : list.getModFileById(MOD_ID);
        if (file == null || file.getMods().isEmpty()) {
            return null;
        }
        return file.getMods().get(0).getVersion().toString();
    }

    /** The internals of the Diorama being loaded, or null when the integration must stay off. */
    static Internals internalsAtLoad() {
        String v = loadingVersion();
        return FMLEnvironment.dist.isClient() && v != null ? VERSIONS.get(v) : null;
    }

    /** True on a client with a supported Diorama. */
    public static boolean active() {
        return FMLEnvironment.dist.isClient() && ModList.get().getModContainerById(MOD_ID)
                .map(c -> SUPPORTED.contains(c.getModInfo().getVersion().toString())).orElse(false);
    }

    public static boolean installed() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static String installedVersion() {
        return ModList.get().getModContainerById(MOD_ID).map(c -> c.getModInfo().getVersion().toString()).orElse("-");
    }
}
