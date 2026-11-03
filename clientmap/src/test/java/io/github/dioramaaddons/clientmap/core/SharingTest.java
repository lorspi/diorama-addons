package io.github.dioramaaddons.clientmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SharingTest {
    private final UUID alice = UUID.randomUUID();
    private final UUID friends = UUID.randomUUID();
    private final UUID miners = UUID.randomUUID();

    @Test
    void exploredPoolsFollowTheModeAndEveryGroup() {
        assertEquals(List.of(), Sharing.exploredReadPools(ShareMode.DISABLED, List.of(friends)));
        assertEquals(List.of("global"), Sharing.exploredReadPools(ShareMode.GLOBAL, List.of(friends)));
        assertEquals(List.of("group:" + friends, "group:" + miners),
                Sharing.exploredReadPools(ShareMode.GROUPS, List.of(friends, miners)));
        assertEquals(List.of(), Sharing.exploredReadPools(ShareMode.GROUPS, List.of()));
    }

    @Test
    void ownPoolAlwaysGetsTheChunkButSharedPoolsOnlyWhenSharing() {
        assertEquals(List.of("player:" + alice, "group:" + friends),
                Sharing.exploredWritePools(ShareMode.GROUPS, alice, List.of(friends), true));
        assertEquals(List.of("player:" + alice), Sharing.exploredWritePools(ShareMode.GROUPS, alice, List.of(friends), false));
        assertEquals(List.of("player:" + alice, "global"), Sharing.exploredWritePools(ShareMode.GLOBAL, alice, List.of(), true));
        assertEquals(List.of(), Sharing.exploredWritePools(ShareMode.DISABLED, alice, List.of(friends), true));
    }

    @Test
    void positionsAreSeenByEveryoneOrByPlayersSharingAGroup() {
        assertTrue(Sharing.seesPosition(ShareMode.GLOBAL, List.of(), List.of()));
        assertTrue(Sharing.seesPosition(ShareMode.GROUPS, List.of(friends, miners), List.of(miners)));
        assertFalse(Sharing.seesPosition(ShareMode.GROUPS, List.of(friends), List.of(miners)));
        assertFalse(Sharing.seesPosition(ShareMode.GROUPS, List.of(), List.of()));
        assertFalse(Sharing.seesPosition(ShareMode.DISABLED, List.of(friends), List.of(friends)));
    }

    @Test
    void waypointsCanBeSharedPubliclyOrWithOwnGroupsOnly() {
        assertTrue(Sharing.maySharePool(ShareMode.GROUPS, "", List.of()));
        assertTrue(Sharing.maySharePool(ShareMode.DISABLED, "", List.of()));
        assertTrue(Sharing.maySharePool(ShareMode.GROUPS, "global", List.of()));
        assertTrue(Sharing.maySharePool(ShareMode.GLOBAL, "group:" + friends, List.of(friends)));
        assertFalse(Sharing.maySharePool(ShareMode.GROUPS, "group:" + miners, List.of(friends)));
        assertFalse(Sharing.maySharePool(ShareMode.GROUPS, "player:" + alice, List.of(friends)));
        assertFalse(Sharing.maySharePool(ShareMode.DISABLED, "global", List.of()));
    }

    @Test
    void readPoolsIncludePublicGroupsAndOldPersonalPool() {
        assertEquals(List.of("global", "group:" + friends, "player:" + alice),
                Sharing.waypointReadPools(ShareMode.GROUPS, alice, List.of(friends)));
        assertEquals(List.of(), Sharing.waypointReadPools(ShareMode.DISABLED, alice, List.of(friends)));
    }

    @Test
    void groupScopesParse() {
        assertEquals(friends, Sharing.groupOf("group:" + friends));
        assertNull(Sharing.groupOf("global"));
        assertNull(Sharing.groupOf("group:nope"));
    }

    @Test
    void dioramaNamesAreCleaned() {
        assertEquals("Base", SharedWaypoint.cleanName("  Base "));
        assertEquals("ab", SharedWaypoint.cleanName("a§b"));
        assertEquals("?", SharedWaypoint.cleanName(""));
        assertEquals(SharedWaypoint.MAX_NAME, SharedWaypoint.cleanName("x".repeat(50)).length());
    }
}
