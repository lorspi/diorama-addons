package io.github.dioramaaddons.claims.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: open the claims screen (sent by {@code /claims menu}). */
public record OpenScreenPayload() implements CustomPacketPayload {
    public static final OpenScreenPayload INSTANCE = new OpenScreenPayload();
    public static final Type<OpenScreenPayload> TYPE = Payloads.type("open_screen");
    public static final StreamCodec<ByteBuf, OpenScreenPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
