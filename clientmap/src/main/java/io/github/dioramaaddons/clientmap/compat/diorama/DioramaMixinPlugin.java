package io.github.dioramaaddons.clientmap.compat.diorama;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Applies the Diorama mixins only on a client with a supported Diorama version, and of the mixins written
 * for one shape of Diorama's internals only those matching the installed version.
 */
public final class DioramaMixinPlugin implements IMixinConfigPlugin {
    private static final String PACKAGE = "io.github.dioramaaddons.clientmap.compat.diorama.mixin.";
    /** Mixins that only fit some versions; every other mixin applies to all supported versions. */
    private static final Map<String, DioramaCompat.Internals> ONLY_FOR = Map.of(
            PACKAGE + "MapScreenSpotV06Mixin", DioramaCompat.Internals.V0_6,
            PACKAGE + "MapScreenSpotV07Mixin", DioramaCompat.Internals.V0_7);

    private DioramaCompat.Internals internals;

    @Override
    public void onLoad(String mixinPackage) {
        internals = DioramaCompat.internalsAtLoad();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (internals == null) {
            return false;
        }
        DioramaCompat.Internals only = ONLY_FOR.get(mixinClassName);
        return only == null || only == internals;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
