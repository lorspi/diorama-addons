package io.github.dioramaaddons.clientmap.server;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import io.github.dioramaaddons.clientmap.config.ClientMapConfig;
import io.github.dioramaaddons.clientmap.core.ExploredLog;
import io.github.dioramaaddons.clientmap.core.GroupRegistry;
import io.github.dioramaaddons.clientmap.core.MapGroup;
import io.github.dioramaaddons.clientmap.core.Scopes;
import io.github.dioramaaddons.clientmap.core.ShareMode;
import io.github.dioramaaddons.clientmap.core.SharedWaypoint;
import io.github.dioramaaddons.clientmap.network.CmPayloads;
import io.github.dioramaaddons.claimsapi.ClaimChangeListener;
import io.github.dioramaaddons.claimsapi.ClaimInfo;
import io.github.dioramaaddons.claimsapi.ClaimsApi;
import io.github.dioramaaddons.claimsapi.ClaimsApiProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Server side of clientmap. Players opt in with {@link CmPayloads.Hello} (sent only by clients with a
 * supported map mod); nothing is sent to anyone else, so vanilla clients and clients without Diorama are
 * unaffected. Server thread only, except where noted.
 */
public final class ClientMapServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile @Nullable ClientMapServer instance;

    static final Codec<PalettedContainer<BlockState>> BLOCK_CODEC = PalettedContainer.codecRW(
            Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());

    private final MinecraftServer server;
    private final ClientMapData data;
    private final Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec;
    private final Map<UUID, Client> clients = new HashMap<>();
    private final ShareMode exploredMode = ClientMapConfig.EXPLORED_MODE.get();
    private final ShareMode locationsMode = ClientMapConfig.LOCATIONS_MODE.get();
    private @Nullable ClaimsApi claimsApi;
    private final ClaimChangeListener claimListener = this::onClaimChanged;
    private final Map<UUID, Optional<ClaimInfo>> pendingClaims = new LinkedHashMap<>();

    /** Per connected player that sent Hello. */
    private static final class Client {
        @Nullable Stream stream;
        String waypointsHash = "";
    }

    /** Explored chunks being sent to one player. */
    private static final class Stream {
        final String dimension;
        final String scope;
        int next;
        double budget;
        boolean inFlight;

        Stream(String dimension, String scope, int next) {
            this.dimension = dimension;
            this.scope = scope;
            this.next = next;
        }
    }

    private ClientMapServer(MinecraftServer server) {
        this.server = server;
        this.data = server.overworld().getDataStorage().computeIfAbsent(ClientMapData.FACTORY, ClientMapData.NAME);
        Registry<Biome> biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        this.biomeCodec = PalettedContainer.codecRO(biomes.asHolderIdMap(), biomes.holderByNameCodec(),
                PalettedContainer.Strategy.SECTION_BIOMES, biomes.getHolderOrThrow(Biomes.PLAINS));
        if (ClientMapConfig.SHOW_CLAIMS.get()) {
            claimsApi = ClaimsApiProvider.get().orElse(null);
            if (claimsApi != null) {
                claimsApi.addListener(claimListener);
            }
        }
    }

    public static @Nullable ClientMapServer get() {
        return instance;
    }

    public static void start(MinecraftServer server) {
        instance = new ClientMapServer(server);
    }

    public static void stop() {
        ClientMapServer s = instance;
        instance = null;
        if (s != null && s.claimsApi != null) {
            s.claimsApi.removeListener(s.claimListener);
        }
    }

    public ClientMapData data() {
        return data;
    }

    public GroupRegistry groups() {
        return data.groups;
    }

    public ShareMode exploredMode() {
        return exploredMode;
    }

    public ShareMode locationsMode() {
        return locationsMode;
    }

    static String dim(ServerLevel level) {
        return level.dimension().location().toString();
    }

    public @Nullable String exploredScope(UUID player) {
        return Scopes.of(exploredMode, player, data.groups.groupIdOf(player));
    }

    public @Nullable String locationsScope(UUID player) {
        return Scopes.of(locationsMode, player, data.groups.groupIdOf(player));
    }

    public boolean isVisible(UUID player) {
        return data.visibility.getOrDefault(player, ClientMapConfig.DEFAULT_VISIBLE.get());
    }

    public void setVisible(ServerPlayer player, boolean visible) {
        data.visibility.put(player.getUUID(), visible);
        data.setDirty();
        sendSession(player);
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    // ---------------------------------------------------------------- connection

    public void onHello(ServerPlayer player) {
        clients.put(player.getUUID(), new Client());
        sendSession(player);
        if (claimsApi != null) {
            List<CmPayloads.ClaimEntry> all = new ArrayList<>();
            for (ClaimInfo c : claimsApi.allClaims()) {
                all.add(entry(c));
            }
            send(player, new CmPayloads.Claims(true, all, List.of()));
        }
        sendWaypoints(player, true);
    }

    public void onLogout(ServerPlayer player) {
        clients.remove(player.getUUID());
    }

    public void onLogin(ServerPlayer player) {
        // Keep stored names current.
        data.groups.groupOf(player.getUUID()).ifPresent(g -> g.members().put(player.getUUID(), player.getGameProfile().getName()));
    }

    public void sendSession(ServerPlayer player) {
        if (!clients.containsKey(player.getUUID())) {
            return;
        }
        String scope = exploredScope(player.getUUID());
        send(player, new CmPayloads.Session(exploredMode, scope == null ? "" : scope, locationsMode,
                isVisible(player.getUUID()), claimsApi != null));
    }

    /** After group changes: new pools for these players. */
    public void refreshPlayers(Iterable<UUID> players) {
        for (UUID id : players) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            Client c = clients.get(id);
            if (p != null && c != null) {
                c.stream = null;
                sendSession(p);
                sendWaypoints(p, true);
            }
        }
    }

    // ---------------------------------------------------------------- explored map

    /** A chunk was sent to a player: it counts as explored by their pool. */
    public void markExplored(ServerPlayer player, ServerLevel level, ChunkPos pos) {
        String scope = exploredScope(player.getUUID());
        if (scope != null && data.explored(scope, dim(level)).add(pos.toLong())) {
            data.setDirty();
        }
    }

    /** Moves a player's own explored chunks into their new group's pool. */
    public void mergeIntoGroup(UUID player, UUID group) {
        Map<String, ExploredLog> own = data.explored.remove(Scopes.player(player));
        if (own != null) {
            own.forEach((dim, log) -> data.explored(Scopes.group(group), dim).merge(log));
        }
        List<SharedWaypoint> wps = data.waypoints.remove(Scopes.player(player));
        if (wps != null && locationsMode == ShareMode.GROUPS) {
            data.waypoints(Scopes.group(group)).addAll(wps);
        }
        data.setDirty();
    }

    public void onExploredRequest(ServerPlayer player, CmPayloads.ExploredRequest req) {
        Client c = clients.get(player.getUUID());
        String scope = exploredScope(player.getUUID());
        if (c == null || scope == null || !scope.equals(req.scope()) || !req.dimension().equals(dim(player.serverLevel()))) {
            return;
        }
        c.stream = new Stream(req.dimension(), scope, Math.max(0, req.from()));
    }

    private void tickStream(ServerPlayer player, Client c) {
        Stream s = c.stream;
        if (s == null || s.inFlight) {
            return;
        }
        if (!s.dimension.equals(dim(player.serverLevel()))) {
            c.stream = null;
            return;
        }
        ExploredLog log = data.explored(s.scope, s.dimension);
        if (s.next >= log.size()) {
            s.budget = 0;
            return;
        }
        s.budget = Math.min(s.budget + ClientMapConfig.EXPLORED_CHUNKS_PER_SECOND.get() / 20.0, ClientMapConfig.EXPLORED_BATCH.get());
        int n = Math.min((int) s.budget, Math.min(ClientMapConfig.EXPLORED_BATCH.get(), log.size() - s.next));
        if (n < 1) {
            return;
        }
        s.budget -= n;
        ServerLevel level = player.serverLevel();
        List<CompletableFuture<Optional<CmPayloads.ChunkEntry>>> parts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ChunkPos pos = new ChunkPos(log.get(s.next + i));
            LevelChunk live = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (live != null) {
                parts.add(CompletableFuture.completedFuture(Optional.of(fromLive(live))));
            } else {
                parts.add(level.getChunkSource().chunkMap.read(pos)
                        .thenApply(tag -> tag.flatMap(t -> fromSaved(pos, t)))
                        .exceptionally(e -> Optional.empty()));
            }
        }
        int next = s.next + n;
        s.inFlight = true;
        CompletableFuture.allOf(parts.toArray(CompletableFuture[]::new)).whenComplete((v, e) -> server.execute(() -> {
            s.inFlight = false;
            if (c.stream != s) {
                return;
            }
            List<CmPayloads.ChunkEntry> entries = new ArrayList<>();
            for (CompletableFuture<Optional<CmPayloads.ChunkEntry>> f : parts) {
                f.getNow(Optional.empty()).ifPresent(entries::add);
            }
            s.next = next;
            send(player, new CmPayloads.ExploredChunks(s.dimension, s.scope, entries, next));
        }));
    }

    private CmPayloads.ChunkEntry fromLive(LevelChunk chunk) {
        ListTag sections = new ListTag();
        LevelChunkSection[] all = chunk.getSections();
        for (int i = 0; i < all.length; i++) {
            LevelChunkSection section = all[i];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            CompoundTag t = new CompoundTag();
            t.putByte("Y", (byte) (chunk.getMinSection() + i));
            t.put("block_states", BLOCK_CODEC.encodeStart(NbtOps.INSTANCE, section.getStates()).getOrThrow());
            t.put("biomes", biomeCodec.encodeStart(NbtOps.INSTANCE, section.getBiomes()).getOrThrow());
            sections.add(t);
        }
        CompoundTag data = new CompoundTag();
        data.put("sections", sections);
        return new CmPayloads.ChunkEntry(chunk.getPos().x, chunk.getPos().z, data);
    }

    /** Keeps only block and biome sections of a fully generated saved chunk. Any thread. */
    static Optional<CmPayloads.ChunkEntry> fromSaved(ChunkPos pos, CompoundTag tag) {
        String status = tag.getString("Status");
        if (!status.equals("minecraft:full") && !status.equals("full")) {
            return Optional.empty();
        }
        ListTag out = new ListTag();
        for (Tag t : tag.getList("sections", Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) t;
            if (!s.contains("block_states", Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag copy = new CompoundTag();
            copy.putByte("Y", s.getByte("Y"));
            copy.put("block_states", s.getCompound("block_states"));
            if (s.contains("biomes", Tag.TAG_COMPOUND)) {
                copy.put("biomes", s.getCompound("biomes"));
            }
            out.add(copy);
        }
        CompoundTag data = new CompoundTag();
        data.put("sections", out);
        return Optional.of(new CmPayloads.ChunkEntry(pos.x, pos.z, data));
    }

    // ---------------------------------------------------------------- waypoints

    public enum WaypointResult { SUCCESS, DISABLED, INVALID_NAME, LIMIT, NOT_FOUND, NOT_ALLOWED }

    private boolean isGroupOwner(UUID player, String scope) {
        Optional<MapGroup> g = data.groups.groupOf(player);
        return g.isPresent() && g.get().owner().equals(player) && scope.equals(Scopes.group(g.get().id()));
    }

    public boolean canModify(ServerPlayer player, String scope, SharedWaypoint wp) {
        return SharedWaypoint.canModify(wp, player.getUUID(), player.hasPermissions(2), isGroupOwner(player.getUUID(), scope));
    }

    public WaypointResult addWaypoint(ServerPlayer player, String name, String dimension, int x, int y, int z, int color) {
        String scope = locationsScope(player.getUUID());
        if (scope == null) {
            return WaypointResult.DISABLED;
        }
        if (!SharedWaypoint.validName(name)) {
            return WaypointResult.INVALID_NAME;
        }
        List<SharedWaypoint> list = data.waypoints(scope);
        if (list.size() >= ClientMapConfig.MAX_WAYPOINTS.get()) {
            return WaypointResult.LIMIT;
        }
        if (server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension))) == null) {
            return WaypointResult.NOT_FOUND;
        }
        list.add(new SharedWaypoint(UUID.randomUUID(), name.trim(), dimension, x, y, z, color, player.getUUID(),
                player.getGameProfile().getName()));
        data.setDirty();
        broadcastWaypoints();
        return WaypointResult.SUCCESS;
    }

    public @Nullable SharedWaypoint find(ServerPlayer player, String nameOrId) {
        String scope = locationsScope(player.getUUID());
        if (scope == null) {
            return null;
        }
        for (SharedWaypoint w : data.waypoints(scope)) {
            if (w.name().equalsIgnoreCase(nameOrId) || w.id().toString().equals(nameOrId)) {
                return w;
            }
        }
        return null;
    }

    public WaypointResult deleteWaypoint(ServerPlayer player, UUID id) {
        String scope = locationsScope(player.getUUID());
        if (scope == null) {
            return WaypointResult.DISABLED;
        }
        List<SharedWaypoint> list = data.waypoints(scope);
        for (SharedWaypoint w : list) {
            if (w.id().equals(id)) {
                if (!canModify(player, scope, w)) {
                    return WaypointResult.NOT_ALLOWED;
                }
                list.remove(w);
                data.setDirty();
                broadcastWaypoints();
                return WaypointResult.SUCCESS;
            }
        }
        return WaypointResult.NOT_FOUND;
    }

    public WaypointResult renameWaypoint(ServerPlayer player, UUID id, String name) {
        String scope = locationsScope(player.getUUID());
        if (scope == null) {
            return WaypointResult.DISABLED;
        }
        if (!SharedWaypoint.validName(name)) {
            return WaypointResult.INVALID_NAME;
        }
        List<SharedWaypoint> list = data.waypoints(scope);
        for (int i = 0; i < list.size(); i++) {
            SharedWaypoint w = list.get(i);
            if (w.id().equals(id)) {
                if (!canModify(player, scope, w)) {
                    return WaypointResult.NOT_ALLOWED;
                }
                list.set(i, w.withName(name.trim()));
                data.setDirty();
                broadcastWaypoints();
                return WaypointResult.SUCCESS;
            }
        }
        return WaypointResult.NOT_FOUND;
    }

    public List<SharedWaypoint> visibleWaypoints(UUID player) {
        String scope = locationsScope(player);
        return scope == null ? List.of() : List.copyOf(data.waypoints(scope));
    }

    private void broadcastWaypoints() {
        for (UUID id : clients.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                sendWaypoints(p, false);
            }
        }
    }

    private void sendWaypoints(ServerPlayer player, boolean force) {
        Client c = clients.get(player.getUUID());
        if (c == null) {
            return;
        }
        String scope = locationsScope(player.getUUID());
        List<CmPayloads.WaypointEntry> entries = new ArrayList<>();
        if (scope != null) {
            for (SharedWaypoint w : data.waypoints(scope)) {
                entries.add(new CmPayloads.WaypointEntry(w.id(), w.name(), w.dimension(), w.x(), w.y(), w.z(), w.color(),
                        w.ownerName(), canModify(player, scope, w)));
            }
        }
        String hash = entries.toString();
        if (force || !hash.equals(c.waypointsHash)) {
            c.waypointsHash = hash;
            send(player, new CmPayloads.Waypoints(entries));
        }
    }

    // ---------------------------------------------------------------- players

    private void sendPlayers() {
        for (UUID id : clients.keySet()) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(id);
            if (viewer == null) {
                continue;
            }
            List<CmPayloads.PlayerEntry> list = new ArrayList<>();
            String scope = locationsScope(id);
            if (scope != null) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (p == viewer || p.isSpectator() || !isVisible(p.getUUID())) {
                        continue;
                    }
                    if (scope.equals(locationsScope(p.getUUID()))) {
                        list.add(new CmPayloads.PlayerEntry(p.getUUID(), dim(p.serverLevel()), p.getX(), p.getY(), p.getZ()));
                    }
                }
            }
            send(viewer, new CmPayloads.Players(list));
        }
    }

    // ---------------------------------------------------------------- claims

    private void onClaimChanged(ClaimChangeListener.ClaimChange change) {
        pendingClaims.put(change.claim().id(),
                change.type() == ClaimChangeListener.Type.REMOVED ? Optional.empty() : Optional.of(change.claim()));
    }

    private static CmPayloads.ClaimEntry entry(ClaimInfo c) {
        long[] chunks = c.chunks().stream().mapToLong(Long::longValue).toArray();
        return new CmPayloads.ClaimEntry(c.id(), c.name(), c.ownerId(), c.ownerName(), c.dimension(), OwnerColors.rgb(c.ownerId()), chunks);
    }

    private void flushClaims() {
        if (pendingClaims.isEmpty()) {
            return;
        }
        List<CmPayloads.ClaimEntry> updated = new ArrayList<>();
        List<UUID> removed = new ArrayList<>();
        pendingClaims.forEach((id, c) -> {
            if (c.isPresent()) {
                updated.add(entry(c.get()));
            } else {
                removed.add(id);
            }
        });
        pendingClaims.clear();
        CmPayloads.Claims payload = new CmPayloads.Claims(false, updated, removed);
        for (UUID id : clients.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                send(p, payload);
            }
        }
    }

    // ---------------------------------------------------------------- tick

    public void tick() {
        if (clients.isEmpty()) {
            pendingClaims.clear();
            return;
        }
        clients.entrySet().removeIf(e -> server.getPlayerList().getPlayer(e.getKey()) == null);
        for (Map.Entry<UUID, Client> e : clients.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                tickStream(p, e.getValue());
            }
        }
        flushClaims();
        if (server.getTickCount() % ClientMapConfig.PLAYER_UPDATE_TICKS.get() == 0) {
            sendPlayers();
        }
    }

    /** Stable owner colour, same formula as the web map. */
    static final class OwnerColors {
        static int rgb(UUID owner) {
            int h = owner.hashCode();
            h ^= h >>> 16;
            float hue = Math.floorMod(h * 0x9E3779B1, 360) / 360f;
            return java.awt.Color.HSBtoRGB(hue, 0.75f, 0.95f) & 0xFFFFFF;
        }
    }

    @SuppressWarnings("unused")
    private static void log(String msg, Object... args) {
        LOGGER.debug(msg, args);
    }
}
