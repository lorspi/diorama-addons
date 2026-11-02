package io.github.dioramaaddons.webmap.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: send me the web map settings (operators only). */
public record ConfigRequestPayload() implements CustomPacketPayload {
    public static final ConfigRequestPayload INSTANCE = new ConfigRequestPayload();
    public static final Type<ConfigRequestPayload> TYPE = WebMapNetwork.type("config_request");
    public static final StreamCodec<ByteBuf, ConfigRequestPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
