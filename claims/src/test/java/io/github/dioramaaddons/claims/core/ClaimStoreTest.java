package io.github.dioramaaddons.claims.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dioramaaddons.claimsapi.ChunkCoord;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimStoreTest {
    static final String DIM = "minecraft:overworld";
    final ClaimStore store = new ClaimStore();
    final ClaimStore.Actor alice = new ClaimStore.Actor(UUID.randomUUID(), "Alice", false);
    final ClaimStore.Actor bob = new ClaimStore.Actor(UUID.randomUUID(), "Bob", false);
    final ClaimStore.Actor admin = new ClaimStore.Actor(UUID.randomUUID(), "Admin", true);

    static long c(int x, int z) {
        return ChunkCoord.pack(x, z);
    }

    ClaimStore.Outcome claim(ClaimStore.Actor a, int x, int z, ClaimLimits limits) {
        return store.claimChunk(a, DIM, c(x, z), null, null, limits, null);
    }

    @Test
    void cannotClaimSomeoneElsesChunk() {
        claim(alice, 0, 0, ClaimLimits.DEFAULT);
        assertEquals(ClaimResult.ALREADY_CLAIMED, claim(bob, 0, 0, ClaimLimits.DEFAULT).result());
        assertEquals(ClaimResult.ALREADY_YOURS, claim(alice, 0, 0, ClaimLimits.DEFAULT).result());
    }

    @Test
    void adjacentChunksJoinTheSameClaim() {
        Claim first = claim(alice, 0, 0, ClaimLimits.DEFAULT).claim();
        Claim second = claim(alice, 1, 0, ClaimLimits.DEFAULT).claim();
        assertSame(first, second);
        assertEquals(2, first.chunks().size());
        Claim far = claim(alice, 10, 10, ClaimLimits.DEFAULT).claim();
        assertNotEquals(first.id(), far.id());
    }

    @Test
    void chunkLimitCountsAllClaimsOfOwnerOnly() {
        ClaimLimits limits = new ClaimLimits(3, 10, true, 0, 32);
        claim(alice, 0, 0, limits);
        claim(alice, 1, 0, limits);
        claim(alice, 10, 0, limits);
        assertEquals(ClaimResult.CHUNK_LIMIT, claim(alice, 2, 0, limits).result());
        // Being a member of Bob's claim does not count.
        Claim bobs = claim(bob, 50, 50, limits).claim();
        store.setMember(bob, bobs.id(), alice.id(), "Alice", EnumSet.noneOf(Permission.class), limits);
        assertEquals(3, store.chunkCount(alice.id()));
        assertEquals(ClaimResult.SUCCESS, claim(bob, 51, 50, limits).result());
    }

    @Test
    void perPlayerLimitsOverrideServerLimits() {
        // Server: 2 chunks and 1 claim each. Alice was given 4 chunks and 2 claims by an operator.
        ClaimStore limitsStore = new ClaimStore();
        limitsStore.setPlayerLimit(alice.id(), PlayerLimits.Key.CHUNKS, 4);
        limitsStore.setPlayerLimit(alice.id(), PlayerLimits.Key.CLAIMS, 2);
        ClaimLimits limits = new ClaimLimits(2, 1, true, 0, 32, limitsStore.perPlayer());
        claim(alice, 0, 0, limits);
        claim(alice, 1, 0, limits);
        assertEquals(ClaimResult.SUCCESS, claim(alice, 2, 0, limits).result());
        assertEquals(ClaimResult.SUCCESS, claim(alice, 20, 0, limits).result());
        assertEquals(ClaimResult.CHUNK_LIMIT, claim(alice, 3, 0, limits).result());
        claim(bob, 50, 50, limits);
        claim(bob, 51, 50, limits);
        assertEquals(ClaimResult.CHUNK_LIMIT, claim(bob, 52, 50, limits).result());
        // Clearing the own value goes back to the server limit.
        limitsStore.setPlayerLimit(alice.id(), PlayerLimits.Key.CHUNKS, null);
        assertEquals(2, limits.maxChunksFor(alice.id()));
        assertEquals(2, limits.maxClaimsFor(alice.id()));
    }

    @Test
    void settingsNeedTheOwner() {
        Claim c = claim(alice, 0, 0, ClaimLimits.DEFAULT).claim();
        ClaimSettings s = ClaimSettings.DEFAULT.withWelcome("", "&aHello {player}").withAlarms(true, false);
        assertEquals(ClaimResult.NOT_OWNER, store.setSettings(bob, c.id(), s).result());
        assertEquals(ClaimSettings.DEFAULT, c.settings());
        assertEquals(ClaimResult.SUCCESS, store.setSettings(alice, c.id(), s).result());
        assertEquals("&aHello {player}", c.settings().welcome());
        assertTrue(c.settings().alarmOwner());
    }

    @Test
    void settingsTextsAreCleanedAndCut() {
        ClaimSettings s = new ClaimSettings("  T\u00A7itle\n ", "x".repeat(400), "", "", false, false);
        assertEquals("Title", s.welcomeTitle());
        assertEquals(ClaimSettings.MAX_TEXT, s.welcome().length());
    }

    @Test
    void claimLimit() {
        ClaimLimits limits = new ClaimLimits(100, 2, true, 0, 32);
        claim(alice, 0, 0, limits);
        claim(alice, 10, 0, limits);
        assertEquals(ClaimResult.CLAIM_LIMIT, claim(alice, 20, 0, limits).result());
        assertEquals(ClaimResult.SUCCESS, claim(alice, 1, 0, limits).result());
    }

    @Test
    void adminIgnoresLimits() {
        ClaimLimits limits = new ClaimLimits(0, 0, true, 0, 0);
        assertEquals(ClaimResult.SUCCESS, claim(admin, 0, 0, limits).result());
    }

    @Test
    void contiguityIsEnforcedForExplicitTarget() {
        Claim home = claim(alice, 0, 0, ClaimLimits.DEFAULT).claim();
        ClaimStore.Outcome o = store.claimChunk(alice, DIM, c(5, 5), home.id(), null, ClaimLimits.DEFAULT, null);
        assertEquals(ClaimResult.NOT_CONTIGUOUS, o.result());
        ClaimLimits loose = new ClaimLimits(100, 10, false, 0, 32);
        assertEquals(ClaimResult.SUCCESS, store.claimChunk(alice, DIM, c(5, 5), home.id(), null, loose, null).result());
    }

    @Test
    void cannotSplitAClaim() {
        claim(alice, 0, 0, ClaimLimits.DEFAULT);
        claim(alice, 1, 0, ClaimLimits.DEFAULT);
        claim(alice, 2, 0, ClaimLimits.DEFAULT);
        assertEquals(ClaimResult.WOULD_SPLIT, store.unclaimChunk(alice, DIM, c(1, 0), ClaimLimits.DEFAULT).result());
        assertEquals(ClaimResult.SUCCESS, store.unclaimChunk(alice, DIM, c(2, 0), ClaimLimits.DEFAULT).result());
    }

    @Test
    void releasingLastChunkDeletesClaim() {
        Claim home = claim(alice, 0, 0, ClaimLimits.DEFAULT).claim();
        store.unclaimChunk(alice, DIM, c(0, 0), ClaimLimits.DEFAULT);
        assertNull(store.claimAt(DIM, c(0, 0)));
        assertEquals(0, store.claimsOf(alice.id()).size());
        assertEquals(java.util.Optional.empty(), store.claim(home.id()));
    }

    @Test
    void onlyOwnerOrAdminCanEdit() {
        Claim home = claim(alice, 0, 0, ClaimLimits.DEFAULT).claim();
        assertEquals(ClaimResult.NOT_OWNER, store.unclaimChunk(bob, DIM, c(0, 0), ClaimLimits.DEFAULT).result());
        assertEquals(ClaimResult.NOT_OWNER, store.rename(bob, home.id(), "Mine").result());
        assertEquals(ClaimResult.SUCCESS, store.rename(admin, home.id(), "Checked").result());
        assertEquals("Checked", home.name());
    }

    @Test
    void spawnDistance() {
        ClaimLimits limits = new ClaimLimits(100, 10, true, 5, 32);
        assertEquals(ClaimResult.TOO_CLOSE_TO_SPAWN,
                store.claimChunk(alice, DIM, c(4, 0), null, null, limits, c(0, 0)).result());
        assertEquals(ClaimResult.SUCCESS, store.claimChunk(alice, DIM, c(5, 0), null, null, limits, c(0, 0)).result());
    }

    @Test
    void batchClaimInAnyOrder() {
        List<Long> chunks = new ArrayList<>();
        for (int x = 3; x >= 0; x--) {
            for (int z = 0; z < 3; z++) {
                chunks.add(c(x, z));
            }
        }
        ClaimStore.BatchOutcome o = store.claimChunks(alice, DIM, chunks, null, ClaimLimits.DEFAULT, null);
        assertEquals(12, o.changed());
        assertEquals(ClaimResult.SUCCESS, o.firstError());
        assertEquals(1, store.claimsOf(alice.id()).size());
        ClaimStore.BatchOutcome released = store.unclaimChunks(alice, DIM, chunks, ClaimLimits.DEFAULT);
        assertEquals(12, released.changed());
    }

    @Test
    void memberLimitAndOwnerCannotBeMember() {
        ClaimLimits limits = new ClaimLimits(100, 10, true, 0, 1);
        Claim home = claim(alice, 0, 0, limits).claim();
        assertEquals(ClaimResult.CANNOT_ADD_OWNER,
                store.setMember(alice, home.id(), alice.id(), "Alice", EnumSet.noneOf(Permission.class), limits).result());
        assertEquals(ClaimResult.SUCCESS,
                store.setMember(alice, home.id(), bob.id(), "Bob", EnumSet.noneOf(Permission.class), limits).result());
        assertEquals(ClaimResult.MEMBER_LIMIT, store.setMember(alice, home.id(), UUID.randomUUID(), "Carol",
                EnumSet.noneOf(Permission.class), limits).result());
    }

    @Test
    void nameValidation() {
        assertEquals(false, ClaimStore.isValidName(" "));
        assertEquals(false, ClaimStore.isValidName("x".repeat(33)));
        assertEquals(false, ClaimStore.isValidName("bad§c"));
        assertEquals(true, ClaimStore.isValidName("Casa de campo"));
    }

    @Test
    void onlyAdminsCanTransferAClaim() {
        Claim alices = claim(alice, 0, 0, ClaimLimits.DEFAULT).claim();
        assertEquals(ClaimResult.NOT_OWNER, store.transfer(alice, alices.id(), bob.id(), "Bob").result());
        assertEquals(ClaimResult.NOT_OWNER, store.transfer(bob, alices.id(), bob.id(), "Bob").result());
        assertEquals(ClaimResult.CLAIM_NOT_FOUND, store.transfer(admin, UUID.randomUUID(), bob.id(), "Bob").result());
        assertEquals(ClaimResult.ALREADY_OWNER, store.transfer(admin, alices.id(), alice.id(), "Alice").result());
    }

    @Test
    void transferChangesOwnerAndDropsTheNewOwnersMemberEntry() {
        Claim claim = claim(alice, 0, 0, ClaimLimits.DEFAULT).claim();
        store.setMember(alice, claim.id(), bob.id(), "Bob", EnumSet.of(Permission.BREAK_BLOCKS), ClaimLimits.DEFAULT);
        List<ClaimStore.ChangeType> events = new ArrayList<>();
        store.addListener((type, c) -> events.add(type));
        assertEquals(ClaimResult.SUCCESS, store.transfer(admin, claim.id(), bob.id(), "Bob").result());
        assertEquals(bob.id(), claim.owner());
        assertEquals("Bob", claim.ownerName());
        assertEquals(false, claim.isMember(bob.id()));
        assertEquals(List.of(ClaimStore.ChangeType.UPDATED), events);
        assertEquals(0, store.chunkCount(alice.id()));
        assertEquals(1, store.chunkCount(bob.id()));
        // The new owner manages it; the old one no longer does.
        assertEquals(ClaimResult.SUCCESS, store.rename(bob, claim.id(), "Casa de Bob").result());
        assertEquals(ClaimResult.NOT_OWNER, store.rename(alice, claim.id(), "Mía").result());
    }

    @Test
    void adminCanEditAnyClaim() {
        Claim claim = claim(alice, 0, 0, ClaimLimits.DEFAULT).claim();
        assertEquals(ClaimResult.SUCCESS, store.rename(admin, claim.id(), "Revisado").result());
        assertEquals(ClaimResult.SUCCESS, store.setPublicPermissions(admin, claim.id(), EnumSet.of(Permission.PVP)).result());
        assertEquals(ClaimResult.SUCCESS, store.claimChunk(admin, DIM, c(1, 0), claim.id(), null, ClaimLimits.DEFAULT,
                null).result());
        assertEquals(alice.id(), claim.owner());
        assertEquals(2, claim.chunks().size());
        assertEquals(ClaimResult.SUCCESS, store.delete(admin, claim.id()).result());
        assertNull(store.claimAt(DIM, c(0, 0)));
    }
}
