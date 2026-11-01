package io.github.dioramaaddons.clientmap.compat.diorama.mixin;

import dev.diorama.TrackedPlayers;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Access to Diorama's package-private Xaero tracker entry point. */
@Mixin(value = TrackedPlayers.class, remap = false)
public interface TrackedPlayersInvoker {
    @Invoker("update")
    static void dioaddons$update(CompoundTag tag) {
        throw new AssertionError();
    }
}
