package io.github.dioramaaddons.claimsapi;

import java.util.Set;
import java.util.UUID;

/**
 * Read-only, immutable snapshot of a claim. Members and permissions are intentionally not exposed.
 *
 * @param dimension dimension id, for example {@code minecraft:overworld}
 * @param chunks    packed chunk coordinates (see {@link ChunkCoord#pack(int, int)})
 */
public record ClaimInfo(UUID id, String name, UUID ownerId, String ownerName, String dimension, Set<Long> chunks) {
    public ClaimInfo {
        chunks = Set.copyOf(chunks);
    }
}
