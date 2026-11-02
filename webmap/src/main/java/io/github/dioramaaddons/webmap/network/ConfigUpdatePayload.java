package io.github.dioramaaddons.webmap.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: save these web map settings (operators only; checked again on the server). */
public record ConfigUpdatePayload(WebSettings settings) implements CustomPacketPayload {
    public static final Type<ConfigUpdatePayload> TYPE = WebMapNetwork.type("config_update");
    public static final StreamCodec<FriendlyByteBuf, ConfigUpdatePayload> CODEC = StreamCodec.ofMember(
            (p, buf) -> p.settings.write(buf), buf -> new ConfigUpdatePayload(WebSettings.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
