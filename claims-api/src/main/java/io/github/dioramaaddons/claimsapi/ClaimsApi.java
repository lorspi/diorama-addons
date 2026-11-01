package io.github.dioramaaddons.claimsapi;

import java.util.Collection;
import java.util.Optional;

/**
 * Minimal read-only access to land claims. Obtain it with {@link ClaimsApiProvider#get()}; it is only
 * present while a server with the claims mod is running. All methods must be called on the server thread
 * unless stated otherwise.
 */
public interface ClaimsApi {
    /** Claim covering the given chunk, if any. */
    Optional<ClaimInfo> claimAt(String dimension, int chunkX, int chunkZ);

    /** Every claim in a dimension. Returns immutable snapshots. */
    Collection<ClaimInfo> claimsIn(String dimension);

    /** Every claim in every dimension. Returns immutable snapshots. */
    Collection<ClaimInfo> allClaims();

    void addListener(ClaimChangeListener listener);

    void removeListener(ClaimChangeListener listener);
}
