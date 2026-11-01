package io.github.dioramaaddons.claims.gametest;

import io.github.dioramaaddons.claims.ClaimsMod;
import io.github.dioramaaddons.claims.core.Claim;
import io.github.dioramaaddons.claims.core.ClaimLimits;
import io.github.dioramaaddons.claims.core.ClaimStore;
import io.github.dioramaaddons.claims.core.Permission;
import io.github.dioramaaddons.claims.core.PermissionTemplate;
import io.github.dioramaaddons.claims.data.ClaimSavedData;
import io.github.dioramaaddons.claims.server.ClaimsService;
import io.github.dioramaaddons.claimsapi.ClaimsApiProvider;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-world tests run by {@code ./gradlew :claims:runGameTestServer}. Only registered in development
 * environments (NeoForge ignores game tests in production). Each test uses its own batch so claims of one
 * test never overlap another test's area.
 */
@GameTestHolder(ClaimsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClaimsGameTests {
    private static final String TEMPLATE = "floor32";

    private ClaimsGameTests() {}

    /** Relative x of the first column of a chunk inside the 32-wide test area (between 8 and 23). */
    private static int border(GameTestHelper h) {
        int origin = h.absolutePos(BlockPos.ZERO).getX();
        for (int b = 8; b < 24; b++) {
            if (Math.floorMod(origin + b, 16) == 0) {
                return b;
            }
        }
        throw new IllegalStateException();
    }

    private static ClaimsService service(GameTestHelper h) {
        ClaimsService s = ClaimsService.get();
        if (s == null) {
            h.fail("Claims service not running");
        }
        return s;
    }

    /** Claims the chunk east of the border for {@code owner}. */
    private static Claim claimInside(GameTestHelper h, ClaimStore.Actor owner, int b) {
        BlockPos abs = h.absolutePos(new BlockPos(b, 2, 16));
        ClaimStore.Outcome o = service(h).store().claimChunk(owner, ClaimsService.dimensionId(h.getLevel()),
                ChunkPos.asLong(abs), null, "Test", ClaimLimits.DEFAULT, null);
        if (!o.result().ok()) {
            h.fail("Could not claim: " + o.result());
        }
        return o.claim();
    }

    private static void cleanup(GameTestHelper h, Claim claim, ServerPlayer... players) {
        service(h).store().delete(new ClaimStore.Actor(claim.owner(), "cleanup", true), claim.id());
        for (ServerPlayer p : players) {
            h.getLevel().getServer().getPlayerList().remove(p);
        }
    }

    private static ClaimStore.Actor actor(ServerPlayer p) {
        return new ClaimStore.Actor(p.getUUID(), p.getGameProfile().getName(), false);
    }

    @SuppressWarnings("removal")
    @GameTest(template = TEMPLATE, batch = "dioaddons_claims_break")
    public static void breakingRespectsPermissions(GameTestHelper h) {
        int b = border(h);
        ServerPlayer owner = h.makeMockServerPlayerInLevel();
        ServerPlayer friend = h.makeMockServerPlayerInLevel();
        ServerPlayer stranger = h.makeMockServerPlayerInLevel();
        Claim claim = claimInside(h, actor(owner), b);
        ClaimStore store = service(h).store();
        // Friend may do everything except breaking blocks.
        EnumSet<Permission> perms = PermissionTemplate.TRUSTED.permissions();
        perms.remove(Permission.BREAK_BLOCKS);
        store.setMember(actor(owner), claim.id(), friend.getUUID(), "friend", perms, ClaimLimits.DEFAULT);

        BlockPos inside = new BlockPos(b + 2, 2, 16);
        BlockPos outside = new BlockPos(b - 2, 2, 16);
        h.setBlock(inside, Blocks.STONE);
        h.setBlock(outside, Blocks.STONE);

        h.assertFalse(friend.gameMode.destroyBlock(h.absolutePos(inside)), "friend without BREAK_BLOCKS broke a block");
        h.assertFalse(stranger.gameMode.destroyBlock(h.absolutePos(inside)), "stranger broke a block");
        h.assertBlockPresent(Blocks.STONE, inside);
        h.assertTrue(stranger.gameMode.destroyBlock(h.absolutePos(outside)), "stranger could not break outside the claim");
        h.assertTrue(owner.gameMode.destroyBlock(h.absolutePos(inside)), "owner could not break own block");
        h.assertBlockPresent(Blocks.AIR, inside);

        // Grant the permission back: now the friend can break.
        h.setBlock(inside, Blocks.STONE);
        store.setMember(actor(owner), claim.id(), friend.getUUID(), "friend", PermissionTemplate.TRUSTED.permissions(),
                ClaimLimits.DEFAULT);
        h.assertTrue(friend.gameMode.destroyBlock(h.absolutePos(inside)), "friend with BREAK_BLOCKS could not break");
        cleanup(h, claim, owner, friend, stranger);
        h.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = TEMPLATE, batch = "dioaddons_claims_fluid", timeoutTicks = 200)
    public static void fluidsDoNotFlowIntoClaims(GameTestHelper h) {
        int b = border(h);
        ServerPlayer owner = h.makeMockServerPlayerInLevel();
        Claim claim = claimInside(h, actor(owner), b);
        h.setBlock(new BlockPos(b - 2, 2, 16), Blocks.WATER);
        h.runAfterDelay(60, () -> {
            h.assertBlockPresent(Blocks.WATER, new BlockPos(b - 1, 2, 16));
            h.assertBlockNotPresent(Blocks.WATER, new BlockPos(b, 2, 16));
            h.assertBlockNotPresent(Blocks.WATER, new BlockPos(b + 1, 2, 16));
            cleanup(h, claim, owner);
            h.succeed();
        });
    }

    @SuppressWarnings("removal")
    @GameTest(template = TEMPLATE, batch = "dioaddons_claims_explosion")
    public static void explosionsDoNotBreakClaimedBlocks(GameTestHelper h) {
        int b = border(h);
        ServerPlayer owner = h.makeMockServerPlayerInLevel();
        Claim claim = claimInside(h, actor(owner), b);
        BlockPos inside = new BlockPos(b + 6, 2, 16);
        BlockPos outside = new BlockPos(b - 6, 2, 16);
        h.setBlock(inside, Blocks.OAK_PLANKS);
        h.setBlock(outside, Blocks.OAK_PLANKS);
        for (BlockPos p : List.of(inside, outside)) {
            BlockPos abs = h.absolutePos(p.above());
            h.getLevel().explode(null, abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 3.0F, Level.ExplosionInteraction.TNT);
        }
        h.assertBlockPresent(Blocks.OAK_PLANKS, inside);
        h.assertBlockPresent(Blocks.STONE, inside.below());
        h.assertBlockNotPresent(Blocks.OAK_PLANKS, outside);
        cleanup(h, claim, owner);
        h.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = TEMPLATE, batch = "dioaddons_claims_piston")
    public static void pistonsCannotPushIntoClaims(GameTestHelper h) {
        int b = border(h);
        ServerPlayer owner = h.makeMockServerPlayerInLevel();
        Claim claim = claimInside(h, actor(owner), b);
        BlockPos piston = new BlockPos(b - 2, 2, 16);
        BlockPos pushed = new BlockPos(b - 1, 2, 16);
        h.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        h.setBlock(pushed, Blocks.COBBLESTONE);
        h.setBlock(new BlockPos(b - 3, 2, 16), Blocks.REDSTONE_BLOCK);
        // Control: the same machine entirely outside the claim must work.
        h.setBlock(new BlockPos(b - 9, 2, 16), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        h.setBlock(new BlockPos(b - 8, 2, 16), Blocks.COBBLESTONE);
        h.setBlock(new BlockPos(b - 10, 2, 16), Blocks.REDSTONE_BLOCK);
        h.runAfterDelay(10, () -> {
            h.assertBlockPresent(Blocks.COBBLESTONE, pushed);
            h.assertBlockNotPresent(Blocks.COBBLESTONE, new BlockPos(b, 2, 16));
            h.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(b - 7, 2, 16));
            cleanup(h, claim, owner);
            h.succeed();
        });
    }

    @SuppressWarnings("removal")
    @GameTest(template = TEMPLATE, batch = "dioaddons_claims_persist")
    public static void savedDataRoundTripsThroughNbt(GameTestHelper h) {
        int b = border(h);
        ServerPlayer owner = h.makeMockServerPlayerInLevel();
        Claim claim = claimInside(h, actor(owner), b);
        service(h).store().setPublicPermissions(actor(owner), claim.id(), EnumSet.of(Permission.PVP));
        ClaimSavedData original = h.getLevel().getServer().overworld().getDataStorage()
                .computeIfAbsent(ClaimSavedData.FACTORY, ClaimSavedData.NAME);
        CompoundTag tag = original.save(new CompoundTag(), h.getLevel().registryAccess());
        ClaimSavedData copy = ClaimSavedData.FACTORY.deserializer().apply(tag, h.getLevel().registryAccess());
        Claim loaded = copy.store().claim(claim.id()).orElse(null);
        h.assertTrue(loaded != null, "claim missing after NBT round trip");
        h.assertTrue(loaded.chunks().equals(claim.chunks()), "chunks differ");
        h.assertTrue(loaded.flag(Permission.PVP), "public permissions lost");
        cleanup(h, claim, owner);
        h.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = TEMPLATE, batch = "dioaddons_claims_api")
    public static void claimsApiReportsClaims(GameTestHelper h) {
        int b = border(h);
        ServerPlayer owner = h.makeMockServerPlayerInLevel();
        Claim claim = claimInside(h, actor(owner), b);
        BlockPos abs = h.absolutePos(new BlockPos(b, 2, 16));
        var api = ClaimsApiProvider.get().orElse(null);
        h.assertTrue(api != null, "claims-api not registered");
        var info = api.claimAt(ClaimsService.dimensionId(h.getLevel()), abs.getX() >> 4, abs.getZ() >> 4).orElse(null);
        h.assertTrue(info != null && info.id().equals(claim.id()), "claims-api did not return the claim");
        cleanup(h, claim, owner);
        h.succeed();
    }
}
