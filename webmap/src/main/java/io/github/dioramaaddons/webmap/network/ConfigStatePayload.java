package io.github.dioramaaddons.webmap.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: the current web map settings.
 *
 * @param reason          why it is sent: OPEN (open the screen), STATE (answer to a request), SAVED, DENIED
 * @param claimsAvailable a claims mod is running, so the claims layer can be shown
 * @param address         where the web map listens ({@code host:port}), empty when the web server is off
 */
public record ConfigStatePayload(Reason reason, WebSettings settings, boolean claimsAvailable, String address)
        implements CustomPacketPayload {
    public enum Reason { OPEN, STATE, SAVED, DENIED }

    public static final Type<ConfigStatePayload> TYPE = WebMapNetwork.type("config_state");
    public static final StreamCodec<FriendlyByteBuf, ConfigStatePayload> CODEC =
            StreamCodec.ofMember(ConfigStatePayload::write, ConfigStatePayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(reason);
        settings.write(buf);
        buf.writeBoolean(claimsAvailable);
        buf.writeUtf(address, 256);
    }

    private static ConfigStatePayload read(FriendlyByteBuf buf) {
        return new ConfigStatePayload(buf.readEnum(Reason.class), WebSettings.read(buf), buf.readBoolean(),
                buf.readUtf(256));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
