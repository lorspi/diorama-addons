package io.github.dioramaaddons.clientmap.compat.diorama.mixin;

import dev.diorama.Claims;
import dev.diorama.compat.FtbChunks;
import io.github.dioramaaddons.clientmap.compat.diorama.DioramaBridge;
import java.util.Optional;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diorama shows claims only while FTB Chunks is "available" and asks it for each team's colour and name.
 * When FTB Chunks is not installed, our claims answer instead: the "team" is the claim id.
 */
@Mixin(value = FtbChunks.class, remap = false)
public abstract class FtbChunksMixin {
    @Inject(method = "available", at = @At("HEAD"), cancellable = true)
    private static void dioaddons$available(CallbackInfoReturnable<Boolean> cir) {
        if (!FtbChunks.installed() && DioramaBridge.claimsActive()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "team", at = @At("HEAD"), cancellable = true)
    private static void dioaddons$team(UUID id, CallbackInfoReturnable<Optional<Claims.Claim>> cir) {
        Claims.Claim ours = DioramaBridge.team(id);
        if (ours != null) {
            cir.setReturnValue(Optional.of(ours));
        } else if (!FtbChunks.installed()) {
            // Unknown claim and no FTB Teams to ask: it no longer exists.
            cir.setReturnValue(Optional.empty());
        }
    }
}
