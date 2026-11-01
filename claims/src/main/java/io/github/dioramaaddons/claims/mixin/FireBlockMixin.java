package io.github.dioramaaddons.claims.mixin;

import io.github.dioramaaddons.claims.protection.ProtectionHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
    /** Burning a neighbouring block (destroys it or turns it into fire). */
    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void dioaddons_claims$protectBurn(Level level, BlockPos pos, int chance, RandomSource random, int age,
            Direction face, CallbackInfo ci) {
        if (!ProtectionHooks.canFireAffect(level, pos)) {
            ci.cancel();
        }
    }

    /** Spreading to nearby air blocks: zero odds means fire never spawns there. */
    @Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I",
            at = @At("HEAD"), cancellable = true)
    private void dioaddons_claims$protectSpread(LevelReader level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!ProtectionHooks.canFireAffect(level, pos)) {
            cir.setReturnValue(0);
        }
    }
}
