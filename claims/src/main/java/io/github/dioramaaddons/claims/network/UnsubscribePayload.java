package io.github.dioramaaddons.claims.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: the claims screen was closed. */
public record UnsubscribePayload() implements CustomPacketPayload {
    public static final UnsubscribePayload INSTANCE = new UnsubscribePayload();
    public static final Type<UnsubscribePayload> TYPE = Payloads.type("unsubscribe");
    public static final StreamCodec<ByteBuf, UnsubscribePayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
