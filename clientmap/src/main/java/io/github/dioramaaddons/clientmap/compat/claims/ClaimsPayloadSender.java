package io.github.dioramaaddons.clientmap.compat.claims;

import io.github.dioramaaddons.claims.network.ClaimChunksPayload;
import io.github.dioramaaddons.claimsapi.ChunkCoord;
import java.util.List;
import java.util.Optional;
import net.neoforged.neoforge.network.PacketDistributor;

/** References the claims mod's classes; only loaded through {@link ClaimsCompat} when that mod is present. */
final class ClaimsPayloadSender {
    private ClaimsPayloadSender() {}

    static void send(boolean claim, int chunkX, int chunkZ) {
        PacketDistributor.sendToServer(new ClaimChunksPayload(claim, Optional.empty(), List.of(ChunkCoord.pack(chunkX, chunkZ))));
    }
}
