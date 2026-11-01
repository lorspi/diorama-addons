package io.github.dioramaaddons.claims.network;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: edit a claim. Which fields are used depends on {@link Op}:
 * <ul>
 *   <li>RENAME: text = new name</li>
 *   <li>DELETE: nothing</li>
 *   <li>ADD_MEMBER: text = player name, mask = permissions</li>
 *   <li>SET_MEMBER: player, mask</li>
 *   <li>REMOVE_MEMBER: player</li>
 *   <li>SET_PUBLIC: mask</li>
 * </ul>
 */
public record EditClaimPayload(Op op, UUID claimId, Optional<UUID> player, String text, int mask)
        implements CustomPacketPayload {
    public static final int MAX_TEXT = 64;

    public enum Op { RENAME, DELETE, ADD_MEMBER, SET_MEMBER, REMOVE_MEMBER, SET_PUBLIC }

    public static final Type<EditClaimPayload> TYPE = Payloads.type("edit_claim");
    public static final StreamCodec<FriendlyByteBuf, EditClaimPayload> CODEC =
            StreamCodec.ofMember(EditClaimPayload::write, EditClaimPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(op);
        UUIDUtil.STREAM_CODEC.encode(buf, claimId);
        buf.writeOptional(player, (b, id) -> UUIDUtil.STREAM_CODEC.encode(b, id));
        buf.writeUtf(text, MAX_TEXT);
        buf.writeVarInt(mask);
    }

    private static EditClaimPayload read(FriendlyByteBuf buf) {
        return new EditClaimPayload(buf.readEnum(Op.class), UUIDUtil.STREAM_CODEC.decode(buf),
                buf.readOptional(b -> UUIDUtil.STREAM_CODEC.decode(b)), buf.readUtf(MAX_TEXT), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
