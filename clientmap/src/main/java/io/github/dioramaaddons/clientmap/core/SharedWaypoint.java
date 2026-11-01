package io.github.dioramaaddons.clientmap.core;

import java.util.UUID;

/** A waypoint everybody in a pool sees. */
public record SharedWaypoint(UUID id, String name, String dimension, int x, int y, int z, int color, UUID owner,
        String ownerName) {
    public static final int MAX_NAME = 32;

    public SharedWaypoint withName(String newName) {
        return new SharedWaypoint(id, newName, dimension, x, y, z, color, owner, ownerName);
    }

    /**
     * Who may rename or delete a waypoint: its owner, a server operator, or the owner of the group whose
     * pool it is in.
     */
    public static boolean canModify(SharedWaypoint wp, UUID actor, boolean operator, boolean groupOwner) {
        return wp.owner().equals(actor) || operator || groupOwner;
    }

    public static boolean validName(String name) {
        return name != null && !name.isBlank() && name.length() <= MAX_NAME && name.chars().noneMatch(c -> c < 0x20 || c == 0xA7);
    }
}
