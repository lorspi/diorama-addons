package io.github.dioramaaddons.claims.protection;

import io.github.dioramaaddons.claims.core.Permission;
import io.github.dioramaaddons.claims.server.ClaimsService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

/**
 * Applies claim rules to game events. Checks only run on the logical server; the client may briefly
 * predict an action that the server then reverts.
 */
public final class ProtectionHandler {
    private ProtectionHandler() {}

    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGH, ProtectionHandler::onBreak);
        bus.addListener(EventPriority.HIGH, ProtectionHandler::onPlace);
        bus.addListener(EventPriority.HIGH, ProtectionHandler::onRightClickBlock);
        bus.addListener(EventPriority.HIGH, ProtectionHandler::onRightClickItem);
        bus.addListener(EventPriority.HIGH, ProtectionHandler::onEntityInteract);
        bus.addListener(EventPriority.HIGH, ProtectionHandler::onEntityInteractSpecific);
        bus.addListener(EventPriority.HIGH, ProtectionHandler::onAttackEntity);
        bus.addListener(EventPriority.HIGH, ProtectionHandler::onLivingDamage);
        bus.addListener(EventPriority.HIGH, ProtectionHandler::onTrample);
        bus.addListener(EventPriority.HIGH, ProtectionHandler::onToolModify);
        bus.addListener(EventPriority.HIGH, ProtectionHandler::onExplosion);
        bus.addListener(EventPriority.HIGH, ProtectionHandler::onPiston);
        bus.addListener(EventPriority.HIGH, ProtectionHandler::onFluidPlaceBlock);
        bus.addListener(EventPriority.HIGH, ProtectionHandler::onMobGriefing);
        bus.addListener(EventPriority.HIGH, ProtectionHandler::onLivingDestroyBlock);
    }

    private static ClaimsService service(Level level) {
        return level == null || level.isClientSide() ? null : ClaimsService.get();
    }

    /** Checks a player permission and tells the player when it is denied. */
    private static boolean check(ClaimsService s, Player player, Level level, BlockPos pos, Permission perm) {
        if (s.allows(player, level, pos, perm)) {
            return true;
        }
        s.notifyDenied(player, level, pos, perm);
        return false;
    }

    // ---------------------------------------------------------------- blocks

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof Level level)) {
            return;
        }
        ClaimsService s = service(level);
        if (s != null && !check(s, e.getPlayer(), level, e.getPos(), Permission.BREAK_BLOCKS)) {
            e.setCanceled(true);
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof Level level)) {
            return;
        }
        ClaimsService s = service(level);
        if (s == null) {
            return;
        }
        Entity entity = e.getEntity();
        if (entity instanceof Player player) {
            if (!check(s, player, level, e.getPos(), Permission.PLACE_BLOCKS)) {
                e.setCanceled(true);
            }
        } else if (entity != null && !s.flag(level, e.getPos(), Permission.MOB_GRIEFING)) {
            e.setCanceled(true);
        }
    }

    private static boolean isContainer(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof Container) {
            return true;
        }
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null;
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        Level level = e.getLevel();
        ClaimsService s = service(level);
        if (s == null) {
            return;
        }
        Player player = e.getEntity();
        BlockPos pos = e.getPos();
        ItemStack stack = e.getItemStack();
        boolean placing = stack.getItem() instanceof BlockItem;

        Permission blockPerm = isContainer(level, pos) ? Permission.OPEN_CONTAINERS : Permission.INTERACT_BLOCKS;
        boolean blockAllowed = s.allows(player, level, pos, blockPerm);
        if (!blockAllowed) {
            e.setUseBlock(TriState.FALSE);
            if (blockPerm == Permission.OPEN_CONTAINERS || !placing) {
                s.notifyDenied(player, level, pos, blockPerm);
            }
        }

        if (!stack.isEmpty() && !placing) {
            BlockPos target = pos.relative(e.getFace() == null ? net.minecraft.core.Direction.UP : e.getFace());
            if (!check(s, player, level, pos, Permission.USE_ITEMS)
                    || !check(s, player, level, target, Permission.USE_ITEMS)) {
                e.setUseItem(TriState.FALSE);
            }
        }

        if (e.getUseBlock() == TriState.FALSE && (e.getUseItem() == TriState.FALSE || stack.isEmpty())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /**
     * Items that change the world when used in the air or on fluids (buckets, boats). Other items used on a
     * block are handled in {@link #onRightClickBlock}; eating or drinking stays allowed.
     */
    private static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        Level level = e.getLevel();
        ClaimsService s = service(level);
        if (s == null) {
            return;
        }
        ItemStack stack = e.getItemStack();
        if (!(stack.getItem() instanceof BucketItem) && !(stack.getItem() instanceof BoatItem)) {
            return;
        }
        Player player = e.getEntity();
        BlockPos target = player.blockPosition();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            target = hit.getBlockPos();
        }
        if (!check(s, player, level, player.blockPosition(), Permission.USE_ITEMS)
                || !check(s, player, level, target, Permission.USE_ITEMS)
                || !check(s, player, level, target.relative(hit.getDirection()), Permission.USE_ITEMS)) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static void onToolModify(BlockEvent.BlockToolModificationEvent e) {
        if (e.isSimulated() || e.getPlayer() == null || !(e.getLevel() instanceof Level level)) {
            return;
        }
        ClaimsService s = service(level);
        if (s != null && !check(s, e.getPlayer(), level, e.getPos(), Permission.USE_ITEMS)) {
            e.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- entities

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        ClaimsService s = service(e.getLevel());
        if (s != null && !check(s, e.getEntity(), e.getLevel(), e.getTarget().blockPosition(), Permission.INTERACT_ENTITIES)) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific e) {
        ClaimsService s = service(e.getLevel());
        if (s != null && !check(s, e.getEntity(), e.getLevel(), e.getTarget().blockPosition(), Permission.INTERACT_ENTITIES)) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** Melee hits on non-living or non-hostile entities (armor stands, item frames, animals). */
    private static void onAttackEntity(AttackEntityEvent e) {
        Player player = e.getEntity();
        Level level = player.level();
        ClaimsService s = service(level);
        Entity target = e.getTarget();
        if (s == null || target instanceof Player || target instanceof Enemy) {
            return;
        }
        Permission perm = target instanceof LivingEntity && !(target instanceof net.minecraft.world.entity.decoration.ArmorStand)
                ? Permission.DAMAGE_ANIMALS : Permission.INTERACT_ENTITIES;
        if (!check(s, player, level, target.blockPosition(), perm)) {
            e.setCanceled(true);
        }
    }

    /** Damage dealt by players, including projectiles: PvP flag and animal protection. */
    private static void onLivingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        Level level = victim.level();
        ClaimsService s = service(level);
        if (s == null || !(e.getSource().getEntity() instanceof Player attacker)) {
            return;
        }
        if (victim instanceof Player) {
            if (attacker == victim) {
                return;
            }
            if (!s.flag(level, victim.blockPosition(), Permission.PVP) || !s.flag(level, attacker.blockPosition(), Permission.PVP)) {
                s.notifyDenied(attacker, level, victim.blockPosition(), Permission.PVP);
                e.setCanceled(true);
            }
        } else if (!(victim instanceof Enemy)) {
            if (!check(s, attacker, level, victim.blockPosition(), Permission.DAMAGE_ANIMALS)) {
                e.setCanceled(true);
            }
        }
    }

    private static void onTrample(BlockEvent.FarmlandTrampleEvent e) {
        if (!(e.getLevel() instanceof Level level)) {
            return;
        }
        ClaimsService s = service(level);
        if (s == null) {
            return;
        }
        Entity entity = e.getEntity();
        Player player = entity instanceof Player p ? p
                : entity.getControllingPassenger() instanceof Player p2 ? p2 : null;
        boolean allowed = player != null
                ? s.allows(player, level, e.getPos(), Permission.TRAMPLE_FARMLAND)
                : s.flag(level, e.getPos(), Permission.MOB_GRIEFING);
        if (!allowed) {
            e.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- environment

    private static void onExplosion(ExplosionEvent.Detonate e) {
        Level level = e.getLevel();
        ClaimsService s = service(level);
        if (s == null) {
            return;
        }
        e.getAffectedBlocks().removeIf(pos -> !s.flag(level, pos, Permission.EXPLOSIONS));
        e.getAffectedEntities().removeIf(entity -> !(entity instanceof Player) && !(entity instanceof Enemy)
                && !s.flag(level, entity.blockPosition(), Permission.EXPLOSIONS));
    }

    private static void onPiston(PistonEvent.Pre e) {
        if (!(e.getLevel() instanceof Level level)) {
            return;
        }
        ClaimsService s = service(level);
        if (s == null) {
            return;
        }
        PistonStructureResolver resolver = e.getStructureHelper();
        if (resolver == null) {
            return;
        }
        BlockPos piston = e.getPos();
        Set<BlockPos> touched = new HashSet<>();
        touched.add(e.getFaceOffsetPos());
        if (resolver.resolve()) {
            net.minecraft.core.Direction dir = resolver.getPushDirection();
            List<BlockPos> toPush = resolver.getToPush();
            for (BlockPos p : toPush) {
                touched.add(p);
                touched.add(p.relative(dir));
            }
            touched.addAll(resolver.getToDestroy());
        }
        for (BlockPos p : touched) {
            if (!s.canCross(level, piston, p)) {
                e.setCanceled(true);
                return;
            }
        }
    }

    /** Lava setting fire to blocks. (Fluids crossing claim borders are stopped by FlowingFluidMixin.) */
    private static void onFluidPlaceBlock(BlockEvent.FluidPlaceBlockEvent e) {
        if (!(e.getLevel() instanceof Level level)) {
            return;
        }
        ClaimsService s = service(level);
        if (s == null) {
            return;
        }
        BlockState newState = e.getNewState();
        if (newState.getBlock() instanceof BaseFireBlock && !s.flag(level, e.getPos(), Permission.FIRE_SPREAD)) {
            e.setNewState(e.getOriginalState());
        }
    }

    private static void onMobGriefing(EntityMobGriefingEvent e) {
        Entity entity = e.getEntity();
        Level level = entity.level();
        ClaimsService s = service(level);
        if (s != null && !(entity instanceof Player) && !s.flag(level, entity.blockPosition(), Permission.MOB_GRIEFING)) {
            e.setCanGrief(false);
        }
    }

    private static void onLivingDestroyBlock(LivingDestroyBlockEvent e) {
        LivingEntity entity = e.getEntity();
        Level level = entity.level();
        ClaimsService s = service(level);
        if (s != null && !(entity instanceof Player) && !s.flag(level, e.getPos(), Permission.MOB_GRIEFING)) {
            e.setCanceled(true);
        }
    }
}
