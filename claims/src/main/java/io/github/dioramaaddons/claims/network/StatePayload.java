package io.github.dioramaaddons.claims.network;

import io.github.dioramaaddons.claims.core.ClaimSettings;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Server → client: everything the claims screen shows.
 *
 * @param cells     (2·radius+1)² entries, row by row from the north-west corner; each is an index into
 *                  {@code gridClaims} or -1 for unclaimed
 * @param manageable claims the player may edit: their own, plus every claim on the server in admin mode
 * @param admin      the player is an operator (may turn admin mode on)
 * @param bypass     admin mode (protection bypass) is on
 */
public record StatePayload(String dimension, int centerX, int centerZ, int radius, List<GridClaim> gridClaims,
        int[] cells, List<ClaimDetail> manageable, int usedChunks, int maxChunks, int usedClaims, int maxClaims,
        boolean admin, boolean bypass, List<String> onlinePlayers, @Nullable Feedback feedback) implements CustomPacketPayload {

    public static final byte OWN = 0;
    public static final byte MEMBER = 1;
    public static final byte OTHER = 2;
    public static final byte ADMIN_EDITABLE = 3;

    public record GridClaim(UUID id, String name, String ownerName, byte relation) {}

    public record Member(UUID id, String name, int mask) {}

    public record ClaimDetail(UUID id, String name, UUID ownerId, String ownerName, String dimension, int chunkCount,
            int publicMask, List<Member> members, ClaimSettings settings) {}

    /** Result of the last action, shown in the screen. */
    public record Feedback(boolean success, String key, List<String> args) {}

    public static final Type<StatePayload> TYPE = Payloads.type("state");
    public static final StreamCodec<FriendlyByteBuf, StatePayload> CODEC =
            StreamCodec.ofMember(StatePayload::write, StatePayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(dimension);
        buf.writeVarInt(centerX);
        buf.writeVarInt(centerZ);
        buf.writeVarInt(radius);
        buf.writeCollection(gridClaims, (b, c) -> {
            UUIDUtil.STREAM_CODEC.encode(b, c.id());
            b.writeUtf(c.name());
            b.writeUtf(c.ownerName());
            b.writeByte(c.relation());
        });
        buf.writeVarIntArray(cells);
        buf.writeCollection(manageable, (b, c) -> {
            UUIDUtil.STREAM_CODEC.encode(b, c.id());
            b.writeUtf(c.name());
            UUIDUtil.STREAM_CODEC.encode(b, c.ownerId());
            b.writeUtf(c.ownerName());
            b.writeUtf(c.dimension());
            b.writeVarInt(c.chunkCount());
            b.writeVarInt(c.publicMask());
            b.writeCollection(c.members(), (b2, m) -> {
                UUIDUtil.STREAM_CODEC.encode(b2, m.id());
                b2.writeUtf(m.name());
                b2.writeVarInt(m.mask());
            });
            ClaimSettingsPayload.writeSettings(b, c.settings());
        });
        buf.writeVarInt(usedChunks);
        buf.writeVarInt(maxChunks);
        buf.writeVarInt(usedClaims);
        buf.writeVarInt(maxClaims);
        buf.writeBoolean(admin);
        buf.writeBoolean(bypass);
        buf.writeCollection(onlinePlayers, FriendlyByteBuf::writeUtf);
        buf.writeBoolean(feedback != null);
        if (feedback != null) {
            buf.writeBoolean(feedback.success());
            buf.writeUtf(feedback.key());
            buf.writeCollection(feedback.args(), FriendlyByteBuf::writeUtf);
        }
    }

    private static StatePayload read(FriendlyByteBuf buf) {
        String dimension = buf.readUtf();
        int cx = buf.readVarInt();
        int cz = buf.readVarInt();
        int radius = buf.readVarInt();
        List<GridClaim> grid = buf.readList(b -> new GridClaim(UUIDUtil.STREAM_CODEC.decode(b), b.readUtf(),
                b.readUtf(), b.readByte()));
        int[] cells = buf.readVarIntArray((2 * 129 + 1) * (2 * 129 + 1));
        List<ClaimDetail> manageable = buf.readList(b -> new ClaimDetail(UUIDUtil.STREAM_CODEC.decode(b),
                b.readUtf(), UUIDUtil.STREAM_CODEC.decode(b), b.readUtf(), b.readUtf(), b.readVarInt(), b.readVarInt(),
                b.readList(b2 -> new Member(UUIDUtil.STREAM_CODEC.decode(b2), b2.readUtf(), b2.readVarInt())),
                ClaimSettingsPayload.readSettings(b)));
        int usedChunks = buf.readVarInt();
        int maxChunks = buf.readVarInt();
        int usedClaims = buf.readVarInt();
        int maxClaims = buf.readVarInt();
        boolean admin = buf.readBoolean();
        boolean bypass = buf.readBoolean();
        List<String> online = buf.readList(FriendlyByteBuf::readUtf);
        Feedback feedback = null;
        if (buf.readBoolean()) {
            feedback = new Feedback(buf.readBoolean(), buf.readUtf(), buf.readList(FriendlyByteBuf::readUtf));
        }
        return new StatePayload(dimension, cx, cz, radius, grid, cells, manageable, usedChunks, maxChunks,
                usedClaims, maxClaims, admin, bypass, online, feedback);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
