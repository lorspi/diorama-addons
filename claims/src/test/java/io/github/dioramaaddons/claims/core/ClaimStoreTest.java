package io.github.dioramaaddons.claims.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

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
}
