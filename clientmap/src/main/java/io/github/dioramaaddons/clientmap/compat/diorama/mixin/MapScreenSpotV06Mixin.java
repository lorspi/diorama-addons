package io.github.dioramaaddons.clientmap.compat.diorama.mixin;

import dev.diorama.MapMenu;
import dev.diorama.MapScreen;
import dev.diorama.render.MapLevel;
import io.github.dioramaaddons.clientmap.compat.diorama.DioramaMenus;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds our lines to the map's right-click menu of a spot, Diorama 0.6.x: {@code spotItems(MapLevel.Spot)}
 * and a private {@code dimension()}. Applied only to those versions (see DioramaMixinPlugin).
 */
@Mixin(value = MapScreen.class, remap = false)
public abstract class MapScreenSpotV06Mixin {
    @Shadow
    private String dimension() {
        throw new AssertionError();
    }

    @Inject(method = "spotItems(Ldev/diorama/render/MapLevel$Spot;)Ljava/util/List;", at = @At("RETURN"),
            cancellable = true)
    private void dioaddons$spotItems(MapLevel.Spot found, CallbackInfoReturnable<List<MapMenu.Item>> cir) {
        if (found.pos() == null) {
            return;
        }
        List<MapMenu.Item> items = new ArrayList<>(cir.getReturnValue());
        DioramaMenus.addSpotItems(items, this.dimension(), found.pos());
        cir.setReturnValue(items);
    }
}
