package io.github.dioramaaddons.claims.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: claim or release chunks in the player's current dimension.
 *
 * @param target claim to extend; empty = automatic (adjacent own claim or a new one)
 * @param chunks packed chunk positions
 */
public record ClaimChunksPayload(boolean claim, Optional<UUID> target, List<Long> chunks)
        implements CustomPacketPayload {
    /** Hard cap checked while decoding; the server also checks the configured radius. */
    public static final int MAX_CHUNKS = 129 * 129;
    public static final Type<ClaimChunksPayload> TYPE = Payloads.type("claim_chunks");
    public static final StreamCodec<FriendlyByteBuf, ClaimChunksPayload> CODEC =
            StreamCodec.ofMember(ClaimChunksPayload::write, ClaimChunksPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(claim);
        buf.writeOptional(target, (b, id) -> UUIDUtil.STREAM_CODEC.encode(b, id));
        buf.writeVarInt(chunks.size());
        for (long c : chunks) {
            buf.writeLong(c);
        }
    }

    private static ClaimChunksPayload read(FriendlyByteBuf buf) {
        boolean claim = buf.readBoolean();
        Optional<UUID> target = buf.readOptional(b -> UUIDUtil.STREAM_CODEC.decode(b));
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_CHUNKS) {
            throw new IllegalArgumentException("Too many chunks: " + n);
        }
        List<Long> chunks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            chunks.add(buf.readLong());
        }
        return new ClaimChunksPayload(claim, target, chunks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
