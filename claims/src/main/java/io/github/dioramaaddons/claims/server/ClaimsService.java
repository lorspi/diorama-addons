package io.github.dioramaaddons.claims.server;

import io.github.dioramaaddons.claims.config.ClaimsConfig;
import io.github.dioramaaddons.claims.core.Claim;
import io.github.dioramaaddons.claims.core.ClaimMember;
import io.github.dioramaaddons.claims.core.ClaimStore;
import io.github.dioramaaddons.claims.core.Permission;
import io.github.dioramaaddons.claims.core.PermissionResolver;
import io.github.dioramaaddons.claims.data.ClaimSavedData;
import io.github.dioramaaddons.claims.network.StatePayload;
import io.github.dioramaaddons.claimsapi.ClaimChangeListener;
import io.github.dioramaaddons.claimsapi.ClaimInfo;
import io.github.dioramaaddons.claimsapi.ClaimsApi;
import io.github.dioramaaddons.claimsapi.ClaimsApiProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Server-side state for one running server. Lives from server start to server stop. */
public final class ClaimsService implements ClaimsApi {
    private static volatile ClaimsService instance;

    private final MinecraftServer server;
    private final ClaimSavedData data;
    private final Set<UUID> bypass = new HashSet<>();
    private final List<ClaimChangeListener> apiListeners = new CopyOnWriteArrayList<>();
    private final Map<UUID, Subscription> subscribers = new HashMap<>();
    private final Map<UUID, Long> lastDenial = new HashMap<>();
    private boolean guiDirty;

    private static final class Subscription {
        int radius;
        long center;
        String dimension;
    }

    private ClaimsService(MinecraftServer server) {
        this.server = server;
        this.data = server.overworld().getDataStorage().computeIfAbsent(ClaimSavedData.FACTORY, ClaimSavedData.NAME);
        store().setDefaultPublicPermissions(ClaimsConfig.defaultPublicPermissions());
        store().addListener(this::onStoreChanged);
    }

    public static @Nullable ClaimsService get() {
        return instance;
    }

    public static void start(MinecraftServer server) {
        ClaimsService service = new ClaimsService(server);
        instance = service;
        ClaimsApiProvider.register(service);
    }

    public static void stop() {
        ClaimsService service = instance;
        if (service != null) {
            ClaimsApiProvider.unregister(service);
        }
        instance = null;
    }

    public MinecraftServer server() {
        return server;
    }

    public ClaimStore store() {
        return data.store();
    }

    public static String dimensionId(Level level) {
        return level.dimension().location().toString();
    }

    // ---------------------------------------------------------------- actors and admin bypass

    public boolean isAdmin(ServerPlayer player) {
        return player.hasPermissions(ClaimsConfig.SERVER.adminPermissionLevel.get());
    }

    public boolean isBypassing(ServerPlayer player) {
        return bypass.contains(player.getUUID()) && isAdmin(player);
    }

    /** Toggles bypass; returns the new state. */
    public boolean toggleBypass(ServerPlayer player) {
        boolean on = !bypass.remove(player.getUUID());
        if (on) {
            bypass.add(player.getUUID());
        }
        guiDirty = true;
        return on;
    }

    public ClaimStore.Actor actor(ServerPlayer player) {
        return new ClaimStore.Actor(player.getUUID(), player.getGameProfile().getName(), isBypassing(player));
    }

    public @Nullable Long spawnChunk(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            return null;
        }
        return ChunkPos.asLong(level.getSharedSpawnPos());
    }

    // ---------------------------------------------------------------- protection checks

    public @Nullable Claim claimAt(Level level, BlockPos pos) {
        return store().claimAt(dimensionId(level), ChunkPos.asLong(pos));
    }

    /** True when {@code player} (null = no player, e.g. a machine) may do {@code permission} at {@code pos}. */
    public boolean allows(@Nullable Player player, Level level, BlockPos pos, Permission permission) {
        Claim claim = claimAt(level, pos);
        if (claim == null) {
            return true;
        }
        if (player instanceof ServerPlayer sp && isBypassing(sp)) {
            return true;
        }
        return PermissionResolver.has(claim, player == null ? null : player.getUUID(), permission);
    }

    /** Claim-wide flag at a position; unclaimed land allows everything. */
    public boolean flag(Level level, BlockPos pos, Permission flag) {
        Claim claim = claimAt(level, pos);
        return claim == null || claim.flag(flag);
    }

    /**
     * True when something may move from {@code from} into {@code to} (fluids, pistons): allowed when the
     * destination is unclaimed or both positions belong to the same owner.
     */
    public boolean canCross(Level level, BlockPos from, BlockPos to) {
        long a = ChunkPos.asLong(from);
        long b = ChunkPos.asLong(to);
        if (a == b) {
            return true;
        }
        String dim = dimensionId(level);
        Claim target = store().claimAt(dim, b);
        if (target == null) {
            return true;
        }
        Claim source = store().claimAt(dim, a);
        return source != null && source.owner().equals(target.owner());
    }

    /** Tells the player why an action failed, at most once per configured cooldown. */
    public void notifyDenied(Player player, Level level, BlockPos pos, Permission permission) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        long now = server.getTickCount();
        Long last = lastDenial.get(sp.getUUID());
        if (last != null && now - last < ClaimsConfig.SERVER.denialMessageCooldownTicks.get()) {
            return;
        }
        lastDenial.put(sp.getUUID(), now);
        Claim claim = claimAt(level, pos);
        String owner = claim == null ? "?" : claim.ownerName();
        Component perm = ServerLang.tr(sp, permission.translationKey());
        sp.displayClientMessage(ServerLang.tr(sp, "dioaddons_claims.denied", owner, perm), true);
    }

    // ---------------------------------------------------------------- players

    public void onPlayerLogin(ServerPlayer player) {
        store().updatePlayerName(player.getUUID(), player.getGameProfile().getName());
    }

    public void onPlayerLogout(ServerPlayer player) {
        subscribers.remove(player.getUUID());
        lastDenial.remove(player.getUUID());
    }

    /** Online player or profile cache lookup by name. */
    public Optional<com.mojang.authlib.GameProfile> findProfile(String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return Optional.of(online.getGameProfile());
        }
        var cache = server.getProfileCache();
        return cache == null ? Optional.empty() : cache.get(name);
    }

    // ---------------------------------------------------------------- GUI sync

    public void subscribe(ServerPlayer player, int requestedRadius) {
        Subscription sub = subscribers.computeIfAbsent(player.getUUID(), id -> new Subscription());
        sub.radius = Math.max(1, Math.min(requestedRadius, ClaimsConfig.SERVER.guiMaxRadius.get()));
        sendState(player, null);
    }

    public void unsubscribe(ServerPlayer player) {
        subscribers.remove(player.getUUID());
    }

    public boolean isSubscribed(ServerPlayer player) {
        return subscribers.containsKey(player.getUUID());
    }

    public void sendState(ServerPlayer player, StatePayload.@Nullable Feedback feedback) {
        Subscription sub = subscribers.get(player.getUUID());
        if (sub == null || !player.connection.hasChannel(StatePayload.TYPE)) {
            // Requests can also come from elsewhere (e.g. the Diorama map): answer on the action bar.
            if (feedback != null) {
                player.displayClientMessage(ServerLang.tr(player, feedback.key(), feedback.args().toArray()), true);
            }
            return;
        }
        ChunkPos center = player.chunkPosition();
        sub.center = center.toLong();
        sub.dimension = dimensionId(player.level());
        PacketDistributor.sendToPlayer(player, buildState(player, center, sub.radius, feedback));
    }

    /** Called every server tick. */
    public void tick() {
        boolean all = guiDirty;
        guiDirty = false;
        if (subscribers.isEmpty()) {
            return;
        }
        boolean checkMoves = server.getTickCount() % 10 == 0;
        for (var entry : List.copyOf(subscribers.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                subscribers.remove(entry.getKey());
                continue;
            }
            Subscription sub = entry.getValue();
            boolean moved = checkMoves && (player.chunkPosition().toLong() != sub.center
                    || !dimensionId(player.level()).equals(sub.dimension));
            if (all || moved) {
                sendState(player, null);
            }
        }
    }

    private StatePayload buildState(ServerPlayer player, ChunkPos center, int radius,
            StatePayload.@Nullable Feedback feedback) {
        String dim = dimensionId(player.level());
        UUID me = player.getUUID();
        boolean admin = isBypassing(player);
        int size = 2 * radius + 1;
        int[] cells = new int[size * size];
        Map<UUID, Integer> gridIndex = new LinkedHashMap<>();
        List<StatePayload.GridClaim> gridClaims = new ArrayList<>();
        Map<UUID, Claim> manageable = new LinkedHashMap<>();
        for (Claim c : store().claimsOf(me)) {
            manageable.put(c.id(), c);
        }
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                long chunk = ChunkPos.asLong(center.x - radius + col, center.z - radius + row);
                Claim c = store().claimAt(dim, chunk);
                if (c == null) {
                    cells[row * size + col] = -1;
                    continue;
                }
                Integer idx = gridIndex.get(c.id());
                if (idx == null) {
                    byte relation;
                    if (c.isOwner(me)) {
                        relation = StatePayload.OWN;
                    } else if (admin) {
                        relation = StatePayload.ADMIN_EDITABLE;
                        manageable.put(c.id(), c);
                    } else if (c.isMember(me)) {
                        relation = StatePayload.MEMBER;
                    } else {
                        relation = StatePayload.OTHER;
                    }
                    idx = gridClaims.size();
                    gridIndex.put(c.id(), idx);
                    gridClaims.add(new StatePayload.GridClaim(c.id(), c.name(), c.ownerName(), relation));
                }
                cells[row * size + col] = idx;
            }
        }
        List<StatePayload.ClaimDetail> details = new ArrayList<>();
        for (Claim c : manageable.values()) {
            List<StatePayload.Member> members = new ArrayList<>();
            for (ClaimMember m : c.members()) {
                members.add(new StatePayload.Member(m.id(), m.name(), Permission.toMask(m.permissions())));
            }
            details.add(new StatePayload.ClaimDetail(c.id(), c.name(), c.ownerName(), c.dimension(),
                    c.chunks().size(), Permission.toMask(c.publicPermissions()), members));
        }
        List<String> online = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.getUUID().equals(me)) {
                online.add(p.getGameProfile().getName());
            }
        }
        var limits = ClaimsConfig.limits();
        return new StatePayload(dim, center.x, center.z, radius, gridClaims, cells, details,
                store().chunkCount(me), limits.maxChunksPerPlayer(), store().claimsOf(me).size(),
                limits.maxClaimsPerPlayer(), admin, online, feedback);
    }

    // ---------------------------------------------------------------- claims-api

    private void onStoreChanged(ClaimStore.ChangeType type, Claim claim) {
        guiDirty = true;
        if (apiListeners.isEmpty()) {
            return;
        }
        ClaimChangeListener.ClaimChange change = new ClaimChangeListener.ClaimChange(
                ClaimChangeListener.Type.valueOf(type.name()), snapshot(claim));
        for (ClaimChangeListener l : apiListeners) {
            try {
                l.onClaimChanged(change);
            } catch (RuntimeException e) {
                com.mojang.logging.LogUtils.getLogger().error("Claim listener failed", e);
            }
        }
    }

    private static ClaimInfo snapshot(Claim c) {
        return new ClaimInfo(c.id(), c.name(), c.owner(), c.ownerName(), c.dimension(), c.chunks());
    }

    @Override
    public Optional<ClaimInfo> claimAt(String dimension, int chunkX, int chunkZ) {
        return Optional.ofNullable(store().claimAt(dimension, ChunkPos.asLong(chunkX, chunkZ))).map(ClaimsService::snapshot);
    }

    @Override
    public Collection<ClaimInfo> claimsIn(String dimension) {
        return store().claimsIn(dimension).stream().map(ClaimsService::snapshot).toList();
    }

    @Override
    public Collection<ClaimInfo> allClaims() {
        return store().all().stream().map(ClaimsService::snapshot).toList();
    }

    @Override
    public void addListener(ClaimChangeListener listener) {
        apiListeners.add(listener);
    }

    @Override
    public void removeListener(ClaimChangeListener listener) {
        apiListeners.remove(listener);
    }
}
