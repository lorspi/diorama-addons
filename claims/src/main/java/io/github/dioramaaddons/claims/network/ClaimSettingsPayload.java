package io.github.dioramaaddons.claims.network;

import io.github.dioramaaddons.claims.core.ClaimSettings;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: the welcome / farewell notifications and visitor alarm of a claim. Validated on the server. */
public record ClaimSettingsPayload(UUID claimId, ClaimSettings settings) implements CustomPacketPayload {
    public static final Type<ClaimSettingsPayload> TYPE = Payloads.type("claim_settings");
    public static final StreamCodec<FriendlyByteBuf, ClaimSettingsPayload> CODEC = StreamCodec.ofMember(
            (p, b) -> {
                UUIDUtil.STREAM_CODEC.encode(b, p.claimId);
                writeSettings(b, p.settings);
            },
            b -> new ClaimSettingsPayload(UUIDUtil.STREAM_CODEC.decode(b), readSettings(b)));

    static void writeSettings(FriendlyByteBuf b, ClaimSettings s) {
        b.writeUtf(s.welcomeTitle(), ClaimSettings.MAX_TITLE * 4);
        b.writeUtf(s.welcome(), ClaimSettings.MAX_TEXT * 4);
        b.writeUtf(s.farewellTitle(), ClaimSettings.MAX_TITLE * 4);
        b.writeUtf(s.farewell(), ClaimSettings.MAX_TEXT * 4);
        b.writeBoolean(s.alarmOwner());
        b.writeBoolean(s.alarmMembers());
    }

    /** Lengths are capped while reading and again by {@link ClaimSettings}'s constructor. */
    static ClaimSettings readSettings(FriendlyByteBuf b) {
        return new ClaimSettings(b.readUtf(ClaimSettings.MAX_TITLE * 4), b.readUtf(ClaimSettings.MAX_TEXT * 4),
                b.readUtf(ClaimSettings.MAX_TITLE * 4), b.readUtf(ClaimSettings.MAX_TEXT * 4), b.readBoolean(),
                b.readBoolean());
    }

    @Override
    public Type<ClaimSettingsPayload> type() {
        return TYPE;
    }
}
