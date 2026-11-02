package io.github.dioramaaddons.claims.network;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: operator actions from the claims screen. The server checks the operator level (and admin
 * mode for edits) again before doing anything.
 * <ul>
 *   <li>TOGGLE_ADMIN_MODE: nothing (same as {@code /claims admin bypass})</li>
 *   <li>TELEPORT: claim</li>
 *   <li>TRANSFER: claim, text = new owner's name</li>
 * </ul>
 */
public record AdminActionPayload(Op op, Optional<UUID> claimId, String text) implements CustomPacketPayload {
    public enum Op { TOGGLE_ADMIN_MODE, TELEPORT, TRANSFER }

    public static final Type<AdminActionPayload> TYPE = Payloads.type("admin_action");
    public static final StreamCodec<FriendlyByteBuf, AdminActionPayload> CODEC =
            StreamCodec.ofMember(AdminActionPayload::write, AdminActionPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(op);
        buf.writeOptional(claimId, (b, id) -> UUIDUtil.STREAM_CODEC.encode(b, id));
        buf.writeUtf(text, EditClaimPayload.MAX_TEXT);
    }

    private static AdminActionPayload read(FriendlyByteBuf buf) {
        return new AdminActionPayload(buf.readEnum(Op.class), buf.readOptional(b -> UUIDUtil.STREAM_CODEC.decode(b)),
                buf.readUtf(EditClaimPayload.MAX_TEXT));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
