package io.github.dioramaaddons.clientmap.compat.diorama.mixin;

import dev.diorama.MapScreen;
import dev.diorama.Settings;
import io.github.dioramaaddons.clientmap.compat.diorama.DioramaBridge;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** "See claims on the map" (map settings screen): the full map reads Diorama's claims setting through this. */
@Mixin(value = MapScreen.class, remap = false)
public abstract class MapScreenClaimsMixin {
    @Redirect(method = "*", at = @At(value = "FIELD", target = "Ldev/diorama/Settings;claims:Ldev/diorama/Settings$ClaimsShown;",
            opcode = Opcodes.GETFIELD))
    private Settings.ClaimsShown dioaddons$claims(Settings settings) {
        return DioramaBridge.claimsOnMap(settings.claims);
    }
}
