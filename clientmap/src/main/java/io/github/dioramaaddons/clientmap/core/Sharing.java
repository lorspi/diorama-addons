package io.github.dioramaaddons.clientmap.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Who shares what with whom on the map. Groups are map groups; the rules accept several per player.
 * Pure logic, no game classes.
 */
public final class Sharing {
    private Sharing() {}

    /** Pools whose explored map a player receives. */
    public static List<String> exploredReadPools(ShareMode mode, Collection<UUID> groups) {
        return switch (mode) {
            case DISABLED -> List.of();
            case GLOBAL -> List.of(Scopes.GLOBAL);
            case GROUPS -> groups.stream().map(Scopes::group).toList();
        };
    }

    /**
     * Pools a chunk explored by the player is added to. The player's own pool always gets it, so turning sharing
     * on later (or joining a group) can hand over everything explored before.
     */
    public static List<String> exploredWritePools(ShareMode mode, UUID player, Collection<UUID> groups, boolean share) {
        if (mode == ShareMode.DISABLED) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        out.add(Scopes.player(player));
        if (share) {
            out.addAll(exploredReadPools(mode, groups));
        }
        return out;
    }

    /** Whether {@code viewer} sees {@code other}'s position (other's own choice to share is checked apart). */
    public static boolean seesPosition(ShareMode mode, Collection<UUID> viewerGroups, Collection<UUID> otherGroups) {
        return switch (mode) {
            case DISABLED -> false;
            case GLOBAL -> true;
            case GROUPS -> viewerGroups.stream().anyMatch(otherGroups::contains);
        };
    }

    /** Pools of shared waypoints a player sees: public ones, those of their groups and their own old personal pool. */
    public static List<String> waypointReadPools(ShareMode mode, UUID player, Collection<UUID> groups) {
        if (mode == ShareMode.DISABLED) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        out.add(Scopes.GLOBAL);
        groups.forEach(g -> out.add(Scopes.group(g)));
        out.add(Scopes.player(player));
        return out;
    }

    /**
     * Whether a player may share a waypoint into {@code scope}: {@code global} (public) or one of their groups.
     * An empty scope means private (stop sharing) and is always allowed.
     */
    public static boolean maySharePool(ShareMode mode, @Nullable String scope, Collection<UUID> groups) {
        if (scope == null || scope.isEmpty()) {
            return true;
        }
        if (mode == ShareMode.DISABLED) {
            return false;
        }
        if (scope.equals(Scopes.GLOBAL)) {
            return true;
        }
        return groups.stream().anyMatch(g -> scope.equals(Scopes.group(g)));
    }

    /** The group id of a {@code group:<id>} scope, or null. */
    public static @Nullable UUID groupOf(String scope) {
        if (scope == null || !scope.startsWith("group:")) {
            return null;
        }
        try {
            return UUID.fromString(scope.substring(6));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
