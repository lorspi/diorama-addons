package io.github.dioramaaddons.clientmap.compat.diorama.mixin;

import dev.diorama.MapMenu;
import dev.diorama.MapScreen;
import dev.diorama.render.MapLevel;
import dev.diorama.waypoint.Waypoint;
import io.github.dioramaaddons.clientmap.compat.diorama.DioramaMenus;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds our lines to the map's right-click menus. */
@Mixin(value = MapScreen.class, remap = false)
public abstract class MapScreenMixin {
    @Shadow
    private String dimension() {
        throw new AssertionError();
    }

    @Inject(method = "spotItems", at = @At("RETURN"), cancellable = true)
    private void dioaddons$spotItems(MapLevel.Spot found, CallbackInfoReturnable<List<MapMenu.Item>> cir) {
        if (found.pos() == null) {
            return;
        }
        List<MapMenu.Item> items = new ArrayList<>(cir.getReturnValue());
        DioramaMenus.addSpotItems(items, this.dimension(), found.pos());
        cir.setReturnValue(items);
    }

    @Inject(method = "waypointItems", at = @At("RETURN"), cancellable = true)
    private void dioaddons$waypointItems(Waypoint waypoint, CallbackInfoReturnable<List<MapMenu.Item>> cir) {
        List<MapMenu.Item> items = new ArrayList<>(cir.getReturnValue());
        if (DioramaMenus.addWaypointItems(items, waypoint)) {
            cir.setReturnValue(items);
        }
    }
}
