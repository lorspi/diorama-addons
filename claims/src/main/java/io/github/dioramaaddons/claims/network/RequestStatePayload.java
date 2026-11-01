package io.github.dioramaaddons.claims.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: the claims screen is open and wants grid state around the player. The player stays
 * subscribed to updates until {@link UnsubscribePayload}. The server clamps the radius.
 */
public record RequestStatePayload(int radius) implements CustomPacketPayload {
    public static final Type<RequestStatePayload> TYPE = Payloads.type("request_state");
    public static final StreamCodec<FriendlyByteBuf, RequestStatePayload> CODEC =
            StreamCodec.ofMember((p, buf) -> buf.writeVarInt(p.radius), buf -> new RequestStatePayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
