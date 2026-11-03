package io.github.dioramaaddons.claims.core;

import java.util.UUID;

/**
 * Configurable limits, read from the server config.
 *
 * @param maxChunksPerPlayer   total chunks a player may own across all claims
 * @param maxClaimsPerPlayer   number of claims a player may own
 * @param requireContiguous    chunks of one claim must share an edge
 * @param minSpawnDistance     minimum Chebyshev distance in chunks from the world spawn chunk (0 = off)
 * @param maxMembersPerClaim   friends per claim
 * @param perPlayer            a player's own limits, set by an operator ({@code /claims admin limits})
 */
public record ClaimLimits(int maxChunksPerPlayer, int maxClaimsPerPlayer, boolean requireContiguous,
        int minSpawnDistance, int maxMembersPerClaim, PerPlayer perPlayer) {
    public static final ClaimLimits DEFAULT = new ClaimLimits(100, 10, true, 0, 32);

    /** Gives a player's own limit for a key, or the server limit when they have none (see {@link PlayerLimits}). */
    @FunctionalInterface
    public interface PerPlayer {
        PerPlayer NONE = (owner, key, serverValue) -> serverValue;

        int get(UUID owner, PlayerLimits.Key key, int serverValue);
    }

    public ClaimLimits(int maxChunksPerPlayer, int maxClaimsPerPlayer, boolean requireContiguous, int minSpawnDistance,
            int maxMembersPerClaim) {
        this(maxChunksPerPlayer, maxClaimsPerPlayer, requireContiguous, minSpawnDistance, maxMembersPerClaim,
                PerPlayer.NONE);
    }

    public int maxChunksFor(UUID owner) {
        return perPlayer.get(owner, PlayerLimits.Key.CHUNKS, maxChunksPerPlayer);
    }

    public int maxClaimsFor(UUID owner) {
        return perPlayer.get(owner, PlayerLimits.Key.CLAIMS, maxClaimsPerPlayer);
    }

    public int maxMembersFor(UUID owner) {
        return perPlayer.get(owner, PlayerLimits.Key.MEMBERS, maxMembersPerClaim);
    }
}
