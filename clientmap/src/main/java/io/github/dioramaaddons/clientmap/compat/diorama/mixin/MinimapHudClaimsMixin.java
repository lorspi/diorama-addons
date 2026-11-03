package io.github.dioramaaddons.clientmap.compat.diorama.mixin;

import dev.diorama.MinimapHud;
import dev.diorama.Settings;
import io.github.dioramaaddons.clientmap.compat.diorama.DioramaBridge;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** "See claims on the minimap" (map settings screen): the minimap reads Diorama's claims setting through this. */
@Mixin(value = MinimapHud.class, remap = false)
public abstract class MinimapHudClaimsMixin {
    @Redirect(method = "*", at = @At(value = "FIELD", target = "Ldev/diorama/Settings;claims:Ldev/diorama/Settings$ClaimsShown;",
            opcode = Opcodes.GETFIELD))
    private Settings.ClaimsShown dioaddons$claims(Settings settings) {
        return DioramaBridge.claimsOnMinimap(settings.claims);
    }
}
