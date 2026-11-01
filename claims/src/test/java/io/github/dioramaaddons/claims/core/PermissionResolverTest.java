package io.github.dioramaaddons.claims.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dioramaaddons.claimsapi.ChunkCoord;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PermissionResolverTest {
    static final String DIM = "minecraft:overworld";
    final UUID owner = UUID.randomUUID();
    final UUID friend = UUID.randomUUID();
    final UUID stranger = UUID.randomUUID();
    ClaimStore store;
    Claim claim;
    ClaimStore.Actor ownerActor;

    @BeforeEach
    void setUp() {
        store = new ClaimStore();
        ownerActor = new ClaimStore.Actor(owner, "Owner", false);
        claim = store.claimChunk(ownerActor, DIM, ChunkCoord.pack(0, 0), null, "Home", ClaimLimits.DEFAULT, null).claim();
    }

    @Test
    void ownerHasEveryPlayerPermission() {
        for (Permission p : Permission.playerPermissions()) {
            assertTrue(PermissionResolver.has(claim, owner, p), p.name());
        }
    }

    @Test
    void strangerHasNothingByDefault() {
        for (Permission p : Permission.values()) {
            assertFalse(PermissionResolver.has(claim, stranger, p), p.name());
        }
    }

    @Test
    void memberUsesOwnPermissions() {
        store.setMember(ownerActor, claim.id(), friend, "Friend", PermissionTemplate.MEMBER.permissions(), ClaimLimits.DEFAULT);
        assertTrue(PermissionResolver.has(claim, friend, Permission.OPEN_CONTAINERS));
        assertFalse(PermissionResolver.has(claim, friend, Permission.BREAK_BLOCKS));
        assertFalse(PermissionResolver.has(claim, stranger, Permission.OPEN_CONTAINERS));
    }

    @Test
    void removingAPermissionFromAMemberDeniesIt() {
        store.setMember(ownerActor, claim.id(), friend, "Friend", PermissionTemplate.TRUSTED.permissions(), ClaimLimits.DEFAULT);
        assertTrue(PermissionResolver.has(claim, friend, Permission.BREAK_BLOCKS));
        EnumSet<Permission> perms = PermissionTemplate.TRUSTED.permissions();
        perms.remove(Permission.BREAK_BLOCKS);
        store.setMember(ownerActor, claim.id(), friend, "Friend", perms, ClaimLimits.DEFAULT);
        assertFalse(PermissionResolver.has(claim, friend, Permission.BREAK_BLOCKS));
        assertTrue(PermissionResolver.has(claim, friend, Permission.PLACE_BLOCKS));
    }

    @Test
    void publicPermissionsApplyToEveryoneAndMembersKeepThem() {
        store.setMember(ownerActor, claim.id(), friend, "Friend", EnumSet.noneOf(Permission.class), ClaimLimits.DEFAULT);
        store.setPublicPermissions(ownerActor, claim.id(), EnumSet.of(Permission.INTERACT_BLOCKS));
        assertTrue(PermissionResolver.has(claim, stranger, Permission.INTERACT_BLOCKS));
        assertTrue(PermissionResolver.has(claim, friend, Permission.INTERACT_BLOCKS));
        assertFalse(PermissionResolver.has(claim, stranger, Permission.BREAK_BLOCKS));
    }

    @Test
    void claimFlagsIgnoreOwnership() {
        assertFalse(PermissionResolver.has(claim, owner, Permission.PVP));
        store.setPublicPermissions(ownerActor, claim.id(), EnumSet.of(Permission.PVP));
        assertTrue(PermissionResolver.has(claim, stranger, Permission.PVP));
    }

    @Test
    void membersCannotReceiveClaimFlags() {
        store.setMember(ownerActor, claim.id(), friend, "Friend", EnumSet.of(Permission.EXPLOSIONS), ClaimLimits.DEFAULT);
        assertTrue(claim.member(friend).orElseThrow().permissions().isEmpty());
    }

    @Test
    void permissionMaskRoundTrips() {
        EnumSet<Permission> set = EnumSet.of(Permission.PVP, Permission.BREAK_BLOCKS, Permission.MOB_GRIEFING);
        assertTrue(Permission.fromMask(Permission.toMask(set)).equals(set));
    }
}
