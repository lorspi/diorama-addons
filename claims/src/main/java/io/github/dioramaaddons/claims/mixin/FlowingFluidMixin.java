package io.github.dioramaaddons.claims.mixin;

import io.github.dioramaaddons.claims.protection.ProtectionHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {
    @Inject(method = "canSpreadTo", at = @At("HEAD"), cancellable = true)
    private void dioaddons_claims$protectBorders(BlockGetter level, BlockPos fromPos, BlockState fromBlockState,
            Direction direction, BlockPos toPos, BlockState toBlockState, FluidState toFluidState, Fluid fluid,
            CallbackInfoReturnable<Boolean> cir) {
        if (!ProtectionHooks.canFluidFlow(level, fromPos, toPos)) {
            cir.setReturnValue(false);
        }
    }
}
