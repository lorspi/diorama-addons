package io.github.dioramaaddons.clientmap.compat.diorama.mixin;

import dev.diorama.waypoint.Waypoint;
import dev.diorama.waypoint.WaypointStore;
import io.github.dioramaaddons.clientmap.compat.diorama.DioramaBridge;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shows shared waypoints like Diorama shows waystones (not saved locally and read-only in its screens), and keeps
 * the player's own shared waypoints in step: changes are sent again and a deleted one stops being shared.
 */
@Mixin(value = WaypointStore.class, remap = false)
public abstract class WaypointStoreMixin {
    @Shadow
    public abstract List<Waypoint> all();

    @Inject(method = "in", at = @At("RETURN"))
    private void dioaddons$in(String dimension, CallbackInfoReturnable<List<Waypoint>> cir) {
        cir.getReturnValue().addAll(DioramaBridge.waypointsIn(dimension));
    }

    @Inject(method = "waystones", at = @At("RETURN"))
    private void dioaddons$waystones(CallbackInfoReturnable<List<Waypoint>> cir) {
        cir.getReturnValue().addAll(DioramaBridge.allWaypoints());
    }

    @Inject(method = "save", at = @At("TAIL"))
    private void dioaddons$saved(CallbackInfo ci) {
        DioramaBridge.syncMine(all());
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void dioaddons$removed(Waypoint waypoint, CallbackInfo ci) {
        DioramaBridge.onRemoved(waypoint);
    }
}
