package io.github.dioramaaddons.clientmap.compat.diorama.mixin;

import dev.diorama.MapMenu;
import dev.diorama.MapScreen;
import dev.diorama.render.MapLevel;
import io.github.dioramaaddons.clientmap.compat.diorama.DioramaMenus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds our lines to the map's right-click menu of a spot, Diorama 0.7.x: {@code spotItems(MapLevel.Spot,
 * BlockPos)}, where the second argument is a rough position used only for spots that are not mapped, and a
 * public {@code dimension()} that names the dimension shown (which can now be another one than the player's).
 * Applied only to those versions (see DioramaMixinPlugin).
 */
@Mixin(value = MapScreen.class, remap = false)
public abstract class MapScreenSpotV07Mixin {
    /** Public in 0.7.0; shadowed so this class also compiles against 0.6.x, where it is private. */
    @Shadow
    public abstract String dimension();

    @Inject(method = "spotItems(Ldev/diorama/render/MapLevel$Spot;Lnet/minecraft/core/BlockPos;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true)
    private void dioaddons$spotItems(MapLevel.Spot found, BlockPos rough,
            CallbackInfoReturnable<List<MapMenu.Item>> cir) {
        if (found.pos() == null) {
            return;
        }
        List<MapMenu.Item> items = new ArrayList<>(cir.getReturnValue());
        DioramaMenus.addSpotItems(items, this.dimension(), found.pos());
        cir.setReturnValue(items);
    }
}
