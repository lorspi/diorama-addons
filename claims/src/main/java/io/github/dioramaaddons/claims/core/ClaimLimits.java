package io.github.dioramaaddons.claims.core;

/**
 * Configurable limits, read from the server config.
 *
 * @param maxChunksPerPlayer   total chunks a player may own across all claims
 * @param maxClaimsPerPlayer   number of claims a player may own
 * @param requireContiguous    chunks of one claim must share an edge
 * @param minSpawnDistance     minimum Chebyshev distance in chunks from the world spawn chunk (0 = off)
 * @param maxMembersPerClaim   friends per claim
 */
public record ClaimLimits(int maxChunksPerPlayer, int maxClaimsPerPlayer, boolean requireContiguous,
        int minSpawnDistance, int maxMembersPerClaim) {
    public static final ClaimLimits DEFAULT = new ClaimLimits(100, 10, true, 0, 32);
}
