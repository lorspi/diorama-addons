package io.github.dioramaaddons.clientmap.core;

import java.util.UUID;

/**
 * A waypoint everybody in a pool sees. {@code linked} ones are copies of a Diorama waypoint of their owner (same id),
 * shared from its edit screen: the owner keeps the original and does not get the copy back. Old ones, made with the
 * former {@code /sharedwaypoint} command, are not linked.
 */
public record SharedWaypoint(UUID id, String name, String dimension, int x, int y, int z, int color, UUID owner,
        String ownerName, boolean linked) {
    public static final int MAX_NAME = 32;

    public SharedWaypoint(UUID id, String name, String dimension, int x, int y, int z, int color, UUID owner,
            String ownerName) {
        this(id, name, dimension, x, y, z, color, owner, ownerName, false);
    }

    public SharedWaypoint withName(String newName) {
        return new SharedWaypoint(id, newName, dimension, x, y, z, color, owner, ownerName, linked);
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

    /** A Diorama name cut to what a shared waypoint keeps, with control and section characters removed. */
    public static String cleanName(String name) {
        StringBuilder b = new StringBuilder();
        for (char c : (name == null ? "" : name.trim()).toCharArray()) {
            if (c >= 0x20 && c != 0xA7) {
                b.append(c);
            }
        }
        String s = b.toString().trim();
        if (s.length() > MAX_NAME) {
            s = s.substring(0, MAX_NAME).trim();
        }
        return s.isEmpty() ? "?" : s;
    }
}
