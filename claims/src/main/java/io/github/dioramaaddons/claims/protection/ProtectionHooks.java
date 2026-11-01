package io.github.dioramaaddons.claims.protection;

import io.github.dioramaaddons.claims.core.Permission;
import io.github.dioramaaddons.claims.server.ClaimsService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.server.level.ServerLevel;

/** Static entry points used by the mixins (fire and fluids have no NeoForge event). */
public final class ProtectionHooks {
    private ProtectionHooks() {}

    /** Fluids may not flow from outside into a claim of a different owner. */
    public static boolean canFluidFlow(BlockGetter getter, BlockPos from, BlockPos to) {
        if (!(getter instanceof ServerLevel level) || (from.getX() >> 4 == to.getX() >> 4 && from.getZ() >> 4 == to.getZ() >> 4)) {
            return true;
        }
        ClaimsService service = ClaimsService.get();
        return service == null || service.canCross(level, from, to);
    }

    /** Fire may not burn or spread to blocks inside claims without the FIRE_SPREAD flag. */
    public static boolean canFireAffect(LevelReader reader, BlockPos pos) {
        if (!(reader instanceof Level level) || level.isClientSide()) {
            return true;
        }
        ClaimsService service = ClaimsService.get();
        return service == null || service.flag(level, pos, Permission.FIRE_SPREAD);
    }
}
