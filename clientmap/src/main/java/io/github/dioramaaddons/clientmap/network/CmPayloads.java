package io.github.dioramaaddons.clientmap.network;

import io.github.dioramaaddons.clientmap.ClientMapMod;
import io.github.dioramaaddons.clientmap.core.ShareMode;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Every clientmap payload. All are registered as optional. */
public final class CmPayloads {
    private CmPayloads() {}

    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ClientMapMod.MOD_ID, "clientmap/" + path));
    }

    private static UUID uuid(FriendlyByteBuf b) {
        return UUIDUtil.STREAM_CODEC.decode(b);
    }

    private static void uuid(FriendlyByteBuf b, UUID id) {
        UUIDUtil.STREAM_CODEC.encode(b, id);
    }

    // ------------------------------------------------------------ client → server

    /** The client has a supported map mod and wants map data. */
    public record Hello(int protocol) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = typeOf("hello");
        public static final StreamCodec<FriendlyByteBuf, Hello> CODEC =
                StreamCodec.ofMember((p, b) -> b.writeVarInt(p.protocol), b -> new Hello(b.readVarInt()));

        @Override
        public Type<Hello> type() {
            return TYPE;
        }
    }

    /** Start sending explored chunks of a dimension from sequence number {@code from}. */
    public record ExploredRequest(String dimension, String scope, int from) implements CustomPacketPayload {
        public static final Type<ExploredRequest> TYPE = typeOf("explored_request");
        public static final StreamCodec<FriendlyByteBuf, ExploredRequest> CODEC = StreamCodec.ofMember(
                (p, b) -> {
                    b.writeUtf(p.dimension, 256);
                    b.writeUtf(p.scope, 64);
                    b.writeVarInt(p.from);
                },
                b -> new ExploredRequest(b.readUtf(256), b.readUtf(64), b.readVarInt()));

        @Override
        public Type<ExploredRequest> type() {
            return TYPE;
        }
    }

    /**
     * Shares one of the player's own Diorama waypoints (same id) into {@code scope}: {@code global} (public),
     * {@code group:<id>}, or empty to stop sharing it. Sent again with the new data when the waypoint changes.
     */
    public record ShareWaypoint(UUID id, String scope, String name, String dimension, int x, int y, int z, int color)
            implements CustomPacketPayload {
        public static final Type<ShareWaypoint> TYPE = typeOf("share_waypoint");
        public static final StreamCodec<FriendlyByteBuf, ShareWaypoint> CODEC = StreamCodec.ofMember(
                (p, b) -> {
                    uuid(b, p.id);
                    b.writeUtf(p.scope, 64);
                    b.writeUtf(p.name, 64);
                    b.writeUtf(p.dimension, 256);
                    b.writeVarInt(p.x);
                    b.writeVarInt(p.y);
                    b.writeVarInt(p.z);
                    b.writeInt(p.color);
                },
                b -> new ShareWaypoint(uuid(b), b.readUtf(64), b.readUtf(64), b.readUtf(256), b.readVarInt(), b.readVarInt(),
                        b.readVarInt(), b.readInt()));

        @Override
        public Type<ShareWaypoint> type() {
            return TYPE;
        }
    }

    public record DeleteWaypoint(UUID id) implements CustomPacketPayload {
        public static final Type<DeleteWaypoint> TYPE = typeOf("delete_waypoint");
        public static final StreamCodec<FriendlyByteBuf, DeleteWaypoint> CODEC =
                StreamCodec.ofMember((p, b) -> uuid(b, p.id), b -> new DeleteWaypoint(uuid(b)));

        @Override
        public Type<DeleteWaypoint> type() {
            return TYPE;
        }
    }

    /** The player's own sharing choices, from the map settings screen: show my location, share what I explore. */
    public record Preferences(boolean visible, boolean shareExplored) implements CustomPacketPayload {
        public static final Type<Preferences> TYPE = typeOf("preferences");
        public static final StreamCodec<FriendlyByteBuf, Preferences> CODEC = StreamCodec.ofMember(
                (p, b) -> {
                    b.writeBoolean(p.visible);
                    b.writeBoolean(p.shareExplored);
                },
                b -> new Preferences(b.readBoolean(), b.readBoolean()));

        @Override
        public Type<Preferences> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------ server → client

    /**
     * Sharing settings and the player's pools. Sent after {@link Hello} and when they change. {@code exploredScopes}
     * are the pools whose explored map the player receives.
     */
    public record Session(ShareMode exploredMode, List<String> exploredScopes, ShareMode locationsMode, boolean visible,
            boolean shareExplored, boolean claims, List<GroupRef> groups) implements CustomPacketPayload {
        public static final Type<Session> TYPE = typeOf("session");
        public static final StreamCodec<FriendlyByteBuf, Session> CODEC = StreamCodec.ofMember(
                (p, b) -> {
                    b.writeEnum(p.exploredMode);
                    b.writeCollection(p.exploredScopes, (b2, sc) -> b2.writeUtf(sc, 64));
                    b.writeEnum(p.locationsMode);
                    b.writeBoolean(p.visible);
                    b.writeBoolean(p.shareExplored);
                    b.writeBoolean(p.claims);
                    b.writeCollection(p.groups, (b2, g) -> {
                        uuid(b2, g.id());
                        b2.writeUtf(g.name(), 64);
                    });
                },
                b -> new Session(b.readEnum(ShareMode.class), b.readList(b2 -> b2.readUtf(64)), b.readEnum(ShareMode.class),
                        b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readList(b2 -> new GroupRef(uuid(b2), b2.readUtf(64)))));

        @Override
        public Type<Session> type() {
            return TYPE;
        }
    }

    /** A map group the player is in, for the waypoint share button. */
    public record GroupRef(UUID id, String name) {}

    public record ClaimEntry(UUID id, String name, UUID owner, String ownerName, String dimension, int rgb, long[] chunks) {}

    /** Claims: with {@code reset} the list replaces everything, otherwise it updates entries; {@code removed} ids are gone. */
    public record Claims(boolean reset, List<ClaimEntry> claims, List<UUID> removed) implements CustomPacketPayload {
        public static final Type<Claims> TYPE = typeOf("claims");
        public static final StreamCodec<FriendlyByteBuf, Claims> CODEC = StreamCodec.ofMember(
                (p, b) -> {
                    b.writeBoolean(p.reset);
                    b.writeCollection(p.claims, (b2, c) -> {
                        uuid(b2, c.id());
                        b2.writeUtf(c.name());
                        uuid(b2, c.owner());
                        b2.writeUtf(c.ownerName());
                        b2.writeUtf(c.dimension());
                        b2.writeInt(c.rgb());
                        b2.writeLongArray(c.chunks());
                    });
                    b.writeCollection(p.removed, CmPayloads::uuid);
                },
                b -> new Claims(b.readBoolean(),
                        b.readList(b2 -> new ClaimEntry(uuid(b2), b2.readUtf(), uuid(b2), b2.readUtf(), b2.readUtf(),
                                b2.readInt(), b2.readLongArray())),
                        b.readList(CmPayloads::uuid)));

        @Override
        public Type<Claims> type() {
            return TYPE;
        }
    }

    public record WaypointEntry(UUID id, String name, String dimension, int x, int y, int z, int color, String ownerName,
            boolean canModify) {}

    /** One of the player's own waypoints shared from Diorama, and where to. */
    public record MyShare(UUID id, String scope) {}

    /**
     * Every shared waypoint the player sees (not their own linked ones), and the pools their own waypoints are shared
     * in. Replaces the previous lists.
     */
    public record Waypoints(List<WaypointEntry> waypoints, List<MyShare> mine) implements CustomPacketPayload {
        public static final Type<Waypoints> TYPE = typeOf("waypoints");
        public static final StreamCodec<FriendlyByteBuf, Waypoints> CODEC = StreamCodec.ofMember(
                (p, b) -> {
                    b.writeCollection(p.waypoints, (b2, w) -> {
                        uuid(b2, w.id());
                        b2.writeUtf(w.name());
                        b2.writeUtf(w.dimension());
                        b2.writeVarInt(w.x());
                        b2.writeVarInt(w.y());
                        b2.writeVarInt(w.z());
                        b2.writeInt(w.color());
                        b2.writeUtf(w.ownerName());
                        b2.writeBoolean(w.canModify());
                    });
                    b.writeCollection(p.mine, (b2, m) -> {
                        uuid(b2, m.id());
                        b2.writeUtf(m.scope(), 64);
                    });
                },
                b -> new Waypoints(
                        b.readList(b2 -> new WaypointEntry(uuid(b2), b2.readUtf(), b2.readUtf(), b2.readVarInt(),
                                b2.readVarInt(), b2.readVarInt(), b2.readInt(), b2.readUtf(), b2.readBoolean())),
                        b.readList(b2 -> new MyShare(uuid(b2), b2.readUtf(64)))));

        @Override
        public Type<Waypoints> type() {
            return TYPE;
        }
    }

    public record PlayerEntry(UUID id, String dimension, double x, double y, double z) {}

    /** Positions of the players this player may see. Replaces the previous list. */
    public record Players(List<PlayerEntry> players) implements CustomPacketPayload {
        public static final Type<Players> TYPE = typeOf("players");
        public static final StreamCodec<FriendlyByteBuf, Players> CODEC = StreamCodec.ofMember(
                (p, b) -> b.writeCollection(p.players, (b2, e) -> {
                    uuid(b2, e.id());
                    b2.writeUtf(e.dimension());
                    b2.writeDouble(e.x());
                    b2.writeDouble(e.y());
                    b2.writeDouble(e.z());
                }),
                b -> new Players(b.readList(b2 -> new PlayerEntry(uuid(b2), b2.readUtf(), b2.readDouble(), b2.readDouble(),
                        b2.readDouble()))));

        @Override
        public Type<Players> type() {
            return TYPE;
        }
    }

    /** Block and biome sections of one chunk: {@code sections} list of {Y, block_states, biomes}. */
    public record ChunkEntry(int x, int z, CompoundTag data) {}

    /** A batch of explored chunks. {@code next} is the sequence number to ask for after this batch. */
    public record ExploredChunks(String dimension, String scope, List<ChunkEntry> chunks, int next)
            implements CustomPacketPayload {
        public static final Type<ExploredChunks> TYPE = typeOf("explored_chunks");
        public static final StreamCodec<FriendlyByteBuf, ExploredChunks> CODEC = StreamCodec.ofMember(
                (p, b) -> {
                    b.writeUtf(p.dimension);
                    b.writeUtf(p.scope);
                    b.writeCollection(p.chunks, (b2, c) -> {
                        b2.writeVarInt(c.x());
                        b2.writeVarInt(c.z());
                        b2.writeNbt(c.data());
                    });
                    b.writeVarInt(p.next);
                },
                b -> new ExploredChunks(b.readUtf(), b.readUtf(),
                        b.readList(b2 -> new ChunkEntry(b2.readVarInt(), b2.readVarInt(), b2.readNbt())), b.readVarInt()));

        @Override
        public Type<ExploredChunks> type() {
            return TYPE;
        }
    }
}
