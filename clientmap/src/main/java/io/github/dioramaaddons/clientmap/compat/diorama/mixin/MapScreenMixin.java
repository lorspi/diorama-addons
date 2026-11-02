package io.github.dioramaaddons.clientmap.compat.diorama.mixin;

import dev.diorama.MapMenu;
import dev.diorama.MapScreen;
import dev.diorama.waypoint.Waypoint;
import io.github.dioramaaddons.clientmap.compat.diorama.DioramaMenus;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds our lines to the map's right-click menu of a waypoint pin. The spot menu changed shape between
 * Diorama versions, so it has one mixin per shape ({@link MapScreenSpotV06Mixin}, {@link MapScreenSpotV07Mixin}).
 */
@Mixin(value = MapScreen.class, remap = false)
public abstract class MapScreenMixin {
    @Inject(method = "waypointItems", at = @At("RETURN"), cancellable = true)
    private void dioaddons$waypointItems(Waypoint waypoint, CallbackInfoReturnable<List<MapMenu.Item>> cir) {
        List<MapMenu.Item> items = new ArrayList<>(cir.getReturnValue());
        if (DioramaMenus.addWaypointItems(items, waypoint)) {
            cir.setReturnValue(items);
        }
    }
}
