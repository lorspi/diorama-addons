package io.github.dioramaaddons.clientmap.server;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import io.github.dioramaaddons.clientmap.config.ClientMapConfig;
import io.github.dioramaaddons.clientmap.core.ExploredLog;
import io.github.dioramaaddons.clientmap.core.Scopes;
import io.github.dioramaaddons.clientmap.core.Sharing;
import io.github.dioramaaddons.clientmap.core.ShareMode;
import io.github.dioramaaddons.clientmap.core.SharedWaypoint;
import io.github.dioramaaddons.clientmap.network.CmPayloads;
import io.github.dioramaaddons.claimsapi.ClaimChangeListener;
import io.github.dioramaaddons.claimsapi.ClaimInfo;
import io.github.dioramaaddons.claimsapi.ClaimsApi;
import io.github.dioramaaddons.claimsapi.ClaimsApiProvider;
import io.github.dioramaaddons.clientmap.core.GroupRegistry;
import io.github.dioramaaddons.clientmap.core.MapGroup;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * unaffected. Groups are the map groups of {@code /mapgroup} ({@link GroupRegistry}); the sharing rules
 * ({@link Sharing}) accept several groups per player, the registry gives at most one.
 * Server thread only, except where noted.
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
        /** Explored chunks being sent, by pool. */
        final Map<String, Stream> streams = new HashMap<>();
        String waypointsHash = "";
    }

    /** Explored chunks of one pool being sent to one player. */
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

    private boolean showClaims() {
        return claimsApi != null;
    }

    private CmPayloads.Claims allClaims() {
        List<CmPayloads.ClaimEntry> all = new ArrayList<>();
        if (showClaims()) {
            for (ClaimInfo c : claimsApi.allClaims()) {
                all.add(entry(c));
            }
        }
        return new CmPayloads.Claims(true, all, List.of());
    }

    static String dim(ServerLevel level) {
        return level.dimension().location().toString();
    }

    // ---------------------------------------------------------------- groups and choices

    /** The map groups a player is in (at most one with {@code /mapgroup}). */
    public List<UUID> groupsOf(UUID player) {
        UUID g = data.groups.groupIdOf(player);
        return g == null ? List.of() : List.of(g);
    }

    private boolean groupExists(UUID id) {
        for (MapGroup g : data.groups.all()) {
            if (g.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** The player's groups as the client shows them in the waypoint share button. */
    private List<CmPayloads.GroupRef> groupRefs(UUID player) {
        return data.groups.groupOf(player).map(g -> List.of(new CmPayloads.GroupRef(g.id(), g.name()))).orElse(List.of());
    }

    public List<String> exploredReadScopes(UUID player) {
        return Sharing.exploredReadPools(exploredMode(), groupsOf(player));
    }

    public boolean isVisible(UUID player) {
        return data.visibility.getOrDefault(player, ClientMapConfig.DEFAULT_VISIBLE.get());
    }

    public void setVisible(ServerPlayer player, boolean visible) {
        data.visibility.put(player.getUUID(), visible);
        data.setDirty();
        sendSession(player);
    }

    public boolean sharesExplored(UUID player) {
        return data.exploredSharing.getOrDefault(player, true);
    }

    public void setSharesExplored(ServerPlayer player, boolean share) {
        data.exploredSharing.put(player.getUUID(), share);
        data.setDirty();
        if (share) {
            handOverExplored(player.getUUID());
        }
        sendSession(player);
    }

    /** Both choices of the map settings screen at once; one session update. */
    public void setPreferences(ServerPlayer player, boolean visible, boolean shareExplored) {
        UUID id = player.getUUID();
        data.visibility.put(id, visible);
        boolean wasSharing = sharesExplored(id);
        data.exploredSharing.put(id, shareExplored);
        data.setDirty();
        if (shareExplored && !wasSharing) {
            handOverExplored(id);
        }
        sendSession(player);
    }

    /** Adds everything the player explored on their own to the pools they now share with. Idempotent. */
    private void handOverExplored(UUID player) {
        if (!sharesExplored(player)) {
            return;
        }
        Map<String, ExploredLog> own = data.explored.get(Scopes.player(player));
        if (own == null) {
            return;
        }
        int added = 0;
        for (String pool : exploredReadScopes(player)) {
            for (Map.Entry<String, ExploredLog> e : own.entrySet()) {
                added += data.explored(pool, e.getKey()).merge(e.getValue());
            }
        }
        if (added > 0) {
            data.setDirty();
        }
    }

    /**
     * The player's map groups changed (or they logged in): new pools, waypoints shared into groups they left are
     * withdrawn, pools of groups that no longer exist are dropped.
     */
    public void onGroupsChanged(ServerPlayer player) {
        onGroupsChanged(player.getUUID());
    }

    /** Same, for a player who may be offline (their shared waypoints still follow the group change). */
    public void onGroupsChanged(UUID playerId) {
        boolean changed = dropGonePools();
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        Set<UUID> mine = new HashSet<>(groupsOf(playerId));
        for (Map.Entry<String, List<SharedWaypoint>> e : data.waypoints.entrySet()) {
            UUID g = Sharing.groupOf(e.getKey());
            if (g != null && !mine.contains(g)) {
                changed |= e.getValue().removeIf(w -> w.linked() && w.owner().equals(playerId));
            }
        }
        if (changed) {
            data.setDirty();
            broadcastWaypoints();
        }
        Client c = clients.get(playerId);
        if (c == null || player == null) {
            return;
        }
        handOverExplored(playerId);
        List<String> scopes = exploredReadScopes(playerId);
        c.streams.keySet().removeIf(sc -> !scopes.contains(sc));
        sendSession(player);
        sendWaypoints(player, false);
    }

    /** Forgets explored maps and waypoints of deleted groups. Returns whether waypoints were dropped. */
    private boolean dropGonePools() {
        boolean waypointsDropped = false;
        for (Iterator<String> it = data.waypoints.keySet().iterator(); it.hasNext(); ) {
            UUID g = Sharing.groupOf(it.next());
            if (g != null && !groupExists(g)) {
                it.remove();
                waypointsDropped = true;
            }
        }
        boolean exploredDropped = data.explored.keySet().removeIf(sc -> {
            UUID g = Sharing.groupOf(sc);
            return g != null && !groupExists(g);
        });
        if (waypointsDropped || exploredDropped) {
            data.setDirty();
        }
        return waypointsDropped;
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    // ---------------------------------------------------------------- connection

    public void onHello(ServerPlayer player) {
        clients.put(player.getUUID(), new Client());
        handOverExplored(player.getUUID());
        sendSession(player);
        if (showClaims()) {
            send(player, allClaims());
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
        UUID id = player.getUUID();
        send(player, new CmPayloads.Session(exploredMode(), exploredReadScopes(id), locationsMode(), isVisible(id),
                sharesExplored(id), showClaims(), groupRefs(id)));
    }

    // ---------------------------------------------------------------- explored map

    /** A chunk was sent to a player: it counts as explored by them and, if they share, by their pools. */
    public void markExplored(ServerPlayer player, ServerLevel level, ChunkPos pos) {
        UUID id = player.getUUID();
        boolean added = false;
        for (String pool : Sharing.exploredWritePools(exploredMode(), id, groupsOf(id), sharesExplored(id))) {
            added |= data.explored(pool, dim(level)).add(pos.toLong());
        }
        if (added) {
            data.setDirty();
        }
    }

    public void onExploredRequest(ServerPlayer player, CmPayloads.ExploredRequest req) {
        Client c = clients.get(player.getUUID());
        if (c == null || !exploredReadScopes(player.getUUID()).contains(req.scope())
                || !req.dimension().equals(dim(player.serverLevel()))) {
            return;
        }
        c.streams.put(req.scope(), new Stream(req.dimension(), req.scope(), Math.max(0, req.from())));
    }

    private void tickStreams(ServerPlayer player, Client c) {
        for (Stream s : List.copyOf(c.streams.values())) {
            tickStream(player, c, s);
        }
    }

    private void tickStream(ServerPlayer player, Client c, Stream s) {
        if (s.inFlight) {
            return;
        }
        if (!s.dimension.equals(dim(player.serverLevel()))) {
            c.streams.remove(s.scope, s);
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
            if (c.streams.get(s.scope) != s) {
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
        UUID g = Sharing.groupOf(scope);
        Optional<MapGroup> group = data.groups.groupOf(player);
        return g != null && group.isPresent() && group.get().id().equals(g) && group.get().owner().equals(player);
    }

    public boolean canModify(ServerPlayer player, String scope, SharedWaypoint wp) {
        return SharedWaypoint.canModify(wp, player.getUUID(), player.hasPermissions(2), isGroupOwner(player.getUUID(), scope));
    }

    private List<String> waypointScopes(UUID player) {
        return Sharing.waypointReadPools(locationsMode(), player, groupsOf(player));
    }

    /**
     * Shares one of the player's Diorama waypoints into a pool, updates it, or stops sharing it (empty scope). The
     * id is the Diorama waypoint's; only its owner can share it.
     */
    public WaypointResult shareWaypoint(ServerPlayer player, CmPayloads.ShareWaypoint p) {
        UUID me = player.getUUID();
        String scope = p.scope() == null ? "" : p.scope();
        if (!Sharing.maySharePool(locationsMode(), scope, groupsOf(me))) {
            return locationsMode() == ShareMode.DISABLED ? WaypointResult.DISABLED : WaypointResult.NOT_ALLOWED;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(p.dimension());
        boolean known = dimension != null && server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension)) != null;
        if (!scope.isEmpty() && !known) {
            return WaypointResult.NOT_FOUND;
        }
        String oldScope = null;
        int oldIndex = -1;
        for (Map.Entry<String, List<SharedWaypoint>> e : data.waypoints.entrySet()) {
            List<SharedWaypoint> list = e.getValue();
            for (int i = 0; i < list.size(); i++) {
                SharedWaypoint w = list.get(i);
                if (w.id().equals(p.id())) {
                    if (!w.owner().equals(me) || !w.linked()) {
                        return WaypointResult.NOT_ALLOWED;
                    }
                    oldScope = e.getKey();
                    oldIndex = i;
                }
            }
        }
        SharedWaypoint wp = new SharedWaypoint(p.id(), SharedWaypoint.cleanName(p.name()), p.dimension(), p.x(), p.y(), p.z(),
                p.color() & 0xFFFFFF, me, player.getGameProfile().getName(), true);
        if (!scope.isEmpty() && !scope.equals(oldScope) && data.waypoints(scope).size() >= ClientMapConfig.MAX_WAYPOINTS.get()) {
            return WaypointResult.LIMIT;
        }
        if (oldScope != null) {
            if (scope.equals(oldScope)) {
                if (data.waypoints(oldScope).get(oldIndex).equals(wp)) {
                    return WaypointResult.SUCCESS;
                }
                data.waypoints(oldScope).set(oldIndex, wp);
            } else {
                data.waypoints(oldScope).remove(oldIndex);
                if (!scope.isEmpty()) {
                    data.waypoints(scope).add(wp);
                }
            }
        } else if (!scope.isEmpty()) {
            data.waypoints(scope).add(wp);
        } else {
            return WaypointResult.SUCCESS;
        }
        data.setDirty();
        broadcastWaypoints();
        return WaypointResult.SUCCESS;
    }

    /** Deletes a shared waypoint the player sees (old ones made with /sharedwaypoint, or others' as an admin). */
    public WaypointResult deleteWaypoint(ServerPlayer player, UUID id) {
        for (String scope : waypointScopes(player.getUUID())) {
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
        }
        return locationsMode() == ShareMode.DISABLED ? WaypointResult.DISABLED : WaypointResult.NOT_FOUND;
    }

    /** Every shared waypoint the player sees, their own included (for {@code /sharedwaypoint list}). */
    public List<SharedWaypoint> visibleWaypoints(UUID player) {
        List<SharedWaypoint> out = new ArrayList<>();
        for (String scope : waypointScopes(player)) {
            out.addAll(data.waypoints(scope));
        }
        return out;
    }

    public @Nullable SharedWaypoint find(UUID player, String nameOrId) {
        for (SharedWaypoint w : visibleWaypoints(player)) {
            if (w.name().equalsIgnoreCase(nameOrId) || w.id().toString().equals(nameOrId)) {
                return w;
            }
        }
        return null;
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
        UUID me = player.getUUID();
        List<CmPayloads.WaypointEntry> entries = new ArrayList<>();
        for (String scope : waypointScopes(me)) {
            for (SharedWaypoint w : data.waypoints(scope)) {
                if (w.linked() && w.owner().equals(me)) {
                    continue;
                }
                entries.add(new CmPayloads.WaypointEntry(w.id(), w.name(), w.dimension(), w.x(), w.y(), w.z(), w.color(),
                        w.ownerName(), canModify(player, scope, w)));
            }
        }
        List<CmPayloads.MyShare> mine = new ArrayList<>();
        data.waypoints.forEach((scope, list) -> {
            for (SharedWaypoint w : list) {
                if (w.linked() && w.owner().equals(me)) {
                    mine.add(new CmPayloads.MyShare(w.id(), scope));
                }
            }
        });
        String hash = entries + "|" + mine;
        if (force || !hash.equals(c.waypointsHash)) {
            c.waypointsHash = hash;
            send(player, new CmPayloads.Waypoints(entries, mine));
        }
    }

    // ---------------------------------------------------------------- players

    private void sendPlayers() {
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        Map<UUID, List<UUID>> groups = new HashMap<>();
        for (ServerPlayer p : online) {
            groups.put(p.getUUID(), groupsOf(p.getUUID()));
        }
        ShareMode mode = locationsMode();
        for (UUID id : clients.keySet()) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(id);
            if (viewer == null) {
                continue;
            }
            List<CmPayloads.PlayerEntry> list = new ArrayList<>();
            for (ServerPlayer p : online) {
                if (p == viewer || p.isSpectator() || !isVisible(p.getUUID())) {
                    continue;
                }
                if (Sharing.seesPosition(mode, groups.getOrDefault(id, List.of()), groups.getOrDefault(p.getUUID(), List.of()))) {
                    list.add(new CmPayloads.PlayerEntry(p.getUUID(), dim(p.serverLevel()), p.getX(), p.getY(), p.getZ()));
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
        if (!showClaims()) {
            pendingClaims.clear();
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
                tickStreams(p, e.getValue());
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
