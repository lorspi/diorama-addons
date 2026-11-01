package io.github.dioramaaddons.clientmap.compat.diorama;

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
    /** Diorama versions the mixins and calls were written and tested against. */
    public static final Set<String> SUPPORTED = Set.of("0.6.0+neoforge-1.21.1");

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

    static boolean supportedAtLoad() {
        String v = loadingVersion();
        return FMLEnvironment.dist.isClient() && v != null && SUPPORTED.contains(v);
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
