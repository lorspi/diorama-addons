package io.github.dioramaaddons.clientmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoreTest {
    final UUID alice = UUID.randomUUID();
    final UUID bob = UUID.randomUUID();
    final UUID carol = UUID.randomUUID();

    @Test
    void scopesFollowTheMode() {
        UUID group = UUID.randomUUID();
        assertNull(Scopes.of(ShareMode.DISABLED, alice, group));
        assertEquals("global", Scopes.of(ShareMode.GLOBAL, alice, group));
        assertEquals("group:" + group, Scopes.of(ShareMode.GROUPS, alice, group));
        assertEquals("player:" + alice, Scopes.of(ShareMode.GROUPS, alice, null));
    }

    @Test
    void exploredLogKeepsFirstOrderAndIgnoresRepeats() {
        ExploredLog log = new ExploredLog();
        assertTrue(log.add(5));
        assertTrue(log.add(1));
        assertFalse(log.add(5));
        assertEquals(2, log.size());
        assertEquals(5, log.get(0));
        ExploredLog copy = ExploredLog.of(log.toArray());
        assertEquals(1, copy.get(1));
        ExploredLog other = ExploredLog.of(new long[] {1, 7, 9});
        assertEquals(2, log.merge(other));
        assertEquals(4, log.size());
        assertEquals(7, log.get(2));
    }

    @Test
    void groupsNeedAnInvitation() {
        GroupRegistry g = new GroupRegistry();
        assertEquals(GroupRegistry.Result.SUCCESS, g.create(alice, "Alice", "Builders"));
        assertEquals(GroupRegistry.Result.NAME_TAKEN, g.create(bob, "Bob", "builders"));
        assertEquals(GroupRegistry.Result.NOT_INVITED, g.join(bob, "Bob", "Builders"));
        assertEquals(GroupRegistry.Result.NOT_IN_GROUP, g.invite(bob, carol));
        assertEquals(GroupRegistry.Result.SUCCESS, g.invite(alice, bob));
        assertEquals(GroupRegistry.Result.SUCCESS, g.join(bob, "Bob", "Builders"));
        assertEquals(GroupRegistry.Result.NOT_OWNER, g.invite(bob, carol));
        assertEquals(g.groupIdOf(alice), g.groupIdOf(bob));
        assertEquals(GroupRegistry.Result.ALREADY_IN_GROUP, g.create(bob, "Bob", "Other"));
    }

    @Test
    void ownerLeavingHandsOverAndEmptyGroupsDisappear() {
        GroupRegistry g = new GroupRegistry();
        g.create(alice, "Alice", "Crew");
        g.invite(alice, bob);
        g.join(bob, "Bob", "Crew");
        assertEquals(GroupRegistry.Result.SUCCESS, g.leave(alice));
        assertEquals(bob, g.groupOf(bob).orElseThrow().owner());
        assertEquals(GroupRegistry.Result.CANNOT_KICK_SELF, g.kick(bob, bob));
        g.leave(bob);
        assertTrue(g.all().isEmpty());
        assertTrue(g.byName("Crew").isEmpty());
    }

    @Test
    void kickRequiresOwnerAndMember() {
        GroupRegistry g = new GroupRegistry();
        g.create(alice, "Alice", "Crew");
        g.invite(alice, bob);
        g.join(bob, "Bob", "Crew");
        assertEquals(GroupRegistry.Result.NOT_OWNER, g.kick(bob, alice));
        assertEquals(GroupRegistry.Result.NOT_A_MEMBER, g.kick(alice, carol));
        assertEquals(GroupRegistry.Result.SUCCESS, g.kick(alice, bob));
        assertNull(g.groupIdOf(bob));
    }

    @Test
    void groupNames() {
        assertTrue(GroupRegistry.validName("Los Amigos_2"));
        assertFalse(GroupRegistry.validName(""));
        assertFalse(GroupRegistry.validName("x".repeat(25)));
        assertFalse(GroupRegistry.validName("bad§name"));
    }

    @Test
    void waypointPermissions() {
        SharedWaypoint w = new SharedWaypoint(UUID.randomUUID(), "Base", "minecraft:overworld", 1, 2, 3, 0, alice, "Alice");
        assertTrue(SharedWaypoint.canModify(w, alice, false, false));
        assertFalse(SharedWaypoint.canModify(w, bob, false, false));
        assertTrue(SharedWaypoint.canModify(w, bob, true, false));
        assertTrue(SharedWaypoint.canModify(w, bob, false, true));
        assertFalse(SharedWaypoint.validName(" "));
        assertTrue(SharedWaypoint.validName("Mina de hierro"));
    }
}
