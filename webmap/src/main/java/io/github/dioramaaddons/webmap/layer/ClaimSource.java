package io.github.dioramaaddons.webmap.layer;

import io.github.dioramaaddons.claimsapi.ClaimInfo;
import java.util.Collection;

/** One provider of land claims for the map. Server thread only. */
interface ClaimSource {
    /** Re-checks whether the provider is running; returns whether it is. Called every few seconds. */
    boolean refresh();

    boolean available();

    /** True once after the claims changed (or the provider appeared or went away). */
    boolean takeDirty();

    Collection<ClaimInfo> claims();

    void stop();
}
