package io.github.dioramaaddons.clientmap.compat.diorama;

import dev.diorama.Claims;
import dev.diorama.MapSession;
import dev.diorama.data.ChunkSnapshot;
import dev.diorama.render.MapLevel;
import dev.diorama.Settings;
import dev.diorama.waypoint.Waypoint;
import io.github.dioramaaddons.clientmap.client.ClientMapClient;
import io.github.dioramaaddons.clientmap.core.Scopes;
import io.github.dioramaaddons.clientmap.core.ShareMode;
import io.github.dioramaaddons.clientmap.config.ClientMapClientConfig;
import io.github.dioramaaddons.clientmap.compat.diorama.mixin.TrackedPlayersInvoker;
import io.github.dioramaaddons.clientmap.network.CmPayloads;
import io.github.dioramaaddons.claimsapi.ChunkCoord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * Every call into Diorama. Loaded only when {@link DioramaCompat#active()} is true. Client thread only.
 */
public final class DioramaBridge {
    /** The "team" Diorama uses for an unclaimed chunk. */
    private static final UUID NO_TEAM = new UUID(0, 0);

    private static final Map<UUID, CmPayloads.ClaimEntry> CLAIMS = new HashMap<>();
    /** dimension → packed chunk → claim id */
    private static final Map<String, Map<Long, UUID>> CLAIM_INDEX = new HashMap<>();
    private static boolean claimsEnabled;

    private static Map<UUID, CmPayloads.WaypointEntry> waypoints = Map.of();
    private static List<Waypoint> waypointObjects = List.of();

    /** Pools the player's own Diorama waypoints are shared in, by waypoint id (from the server). */
    private static Map<UUID, String> myShares = Map.of();
    /** What was last sent for each shared waypoint, so unchanged ones are not sent again. */
    private static final Map<UUID, String> lastSent = new HashMap<>();

    private static Set<UUID> trackedPlayers = Set.of();

    private DioramaBridge() {}

    public static void clear() {
        CLAIMS.clear();
        CLAIM_INDEX.clear();
        claimsEnabled = false;
        waypoints = Map.of();
        waypointObjects = List.of();
        myShares = Map.of();
        lastSent.clear();
        trackedPlayers = Set.of();
    }

    // ---------------------------------------------------------------- claims shown (personal settings)

    /** Diorama's claims setting, narrowed by the player's own map settings for the full map. */
    public static Settings.ClaimsShown claimsOnMap(Settings.ClaimsShown diorama) {
        return ClientMapClientConfig.flag(ClientMapClientConfig.CLAIMS_ON_MAP) ? diorama : Settings.ClaimsShown.OFF;
    }

    /** Same for the minimap. */
    public static Settings.ClaimsShown claimsOnMinimap(Settings.ClaimsShown diorama) {
        return ClientMapClientConfig.flag(ClientMapClientConfig.CLAIMS_ON_MINIMAP) ? diorama : Settings.ClaimsShown.OFF;
    }

    // ---------------------------------------------------------------- claims

    public static boolean claimsActive() {
        return claimsEnabled;
    }

    public static void setClaimsEnabled(boolean enabled) {
        claimsEnabled = enabled;
    }

    private static ResourceKey<Level> key(String dimension) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension));
    }

    private static void forget(CmPayloads.ClaimEntry e, Set<Long> keep) {
        Map<Long, UUID> index = CLAIM_INDEX.getOrDefault(e.dimension(), Map.of());
        for (long c : e.chunks()) {
            if (!keep.contains(c)) {
                index.remove(c, e.id());
                Claims.updated(key(e.dimension()), ChunkCoord.unpackX(c), ChunkCoord.unpackZ(c), NO_TEAM, false);
            }
        }
    }

    public static void onClaims(CmPayloads.Claims payload) {
        claimsEnabled = true;
        if (payload.reset()) {
            for (CmPayloads.ClaimEntry e : List.copyOf(CLAIMS.values())) {
                forget(e, Set.of());
            }
            CLAIMS.clear();
            CLAIM_INDEX.clear();
        }
        for (UUID id : payload.removed()) {
            CmPayloads.ClaimEntry old = CLAIMS.remove(id);
            if (old != null) {
                forget(old, Set.of());
            }
        }
        for (CmPayloads.ClaimEntry e : payload.claims()) {
            Set<Long> now = new HashSet<>();
            for (long c : e.chunks()) {
                now.add(c);
            }
            CmPayloads.ClaimEntry old = CLAIMS.put(e.id(), e);
            if (old != null) {
                forget(old, old.dimension().equals(e.dimension()) ? now : Set.of());
            }
            Map<Long, UUID> index = CLAIM_INDEX.computeIfAbsent(e.dimension(), d -> new HashMap<>());
            for (long c : e.chunks()) {
                index.put(c, e.id());
                Claims.updated(key(e.dimension()), ChunkCoord.unpackX(c), ChunkCoord.unpackZ(c), e.id(), false);
            }
        }
    }

    /** Colour and label of one of our claims, for Diorama's claim layer, or null if unknown. */
    public static @Nullable Claims.Claim team(UUID id) {
        CmPayloads.ClaimEntry e = CLAIMS.get(id);
        return e == null ? null : new Claims.Claim(id, e.rgb(), e.name() + " · " + e.ownerName(), false);
    }

    public static @Nullable CmPayloads.ClaimEntry claimAt(String dimension, int chunkX, int chunkZ) {
        UUID id = CLAIM_INDEX.getOrDefault(dimension, Map.of()).get(ChunkCoord.pack(chunkX, chunkZ));
        return id == null ? null : CLAIMS.get(id);
    }

    // ---------------------------------------------------------------- waypoints

    public static void onWaypoints(CmPayloads.Waypoints payload) {
        Map<UUID, CmPayloads.WaypointEntry> map = new HashMap<>();
        List<Waypoint> objects = new ArrayList<>();
        for (CmPayloads.WaypointEntry e : payload.waypoints()) {
            map.put(e.id(), e);
            Waypoint w = new Waypoint(e.name(), e.x(), e.y(), e.z(), e.dimension(), 0xFF000000 | e.color());
            w.id = e.id();
            w.kind = Waypoint.Kind.WAYSTONE;
            w.visible = true;
            objects.add(w);
        }
        waypoints = map;
        waypointObjects = List.copyOf(objects);
        Map<UUID, String> mine = new HashMap<>();
        for (CmPayloads.MyShare m : payload.mine()) {
            mine.put(m.id(), m.scope());
        }
        myShares = mine;
        lastSent.keySet().retainAll(mine.keySet());
    }

    // ---------------------------------------------------------------- sharing own waypoints

    /** Whether the player can share this waypoint from its edit screen. */
    public static boolean canShare(Waypoint w) {
        CmPayloads.Session session = ClientMapClient.session();
        Minecraft mc = Minecraft.getInstance();
        return w != null && w.editable() && w.id != null && session != null
                && session.locationsMode() != ShareMode.DISABLED && mc.getConnection() != null
                && mc.getConnection().hasChannel(CmPayloads.ShareWaypoint.TYPE);
    }

    /** The pool a waypoint of the player is shared in: {@code ""} (private), {@code global} or {@code group:<id>}. */
    public static String shareOf(UUID id) {
        return id == null ? "" : myShares.getOrDefault(id, "");
    }

    /** The choices of the share button, in order: private, public, then each of the player's map groups. */
    public static List<String> shareChoices() {
        List<String> out = new ArrayList<>();
        out.add("");
        out.add(Scopes.GLOBAL);
        for (CmPayloads.GroupRef g : groups()) {
            out.add(Scopes.group(g.id()));
        }
        return out;
    }

    public static Component shareLabel(String scope) {
        if (scope == null || scope.isEmpty()) {
            return Component.translatable("dioaddons_clientmap.share.private");
        }
        if (scope.equals(Scopes.GLOBAL)) {
            return Component.translatable("dioaddons_clientmap.share.public");
        }
        for (CmPayloads.GroupRef g : groups()) {
            if (scope.equals(Scopes.group(g.id()))) {
                return Component.translatable("dioaddons_clientmap.share.group", g.name());
            }
        }
        return Component.translatable("dioaddons_clientmap.share.group", "?");
    }

    private static List<CmPayloads.GroupRef> groups() {
        CmPayloads.Session session = ClientMapClient.session();
        return session == null ? List.of() : session.groups();
    }

    private static String signature(Waypoint w, String scope) {
        return scope + "|" + w.name + "|" + w.dimension + "|" + w.x + "|" + w.y + "|" + w.z + "|" + (w.color & 0xFFFFFF);
    }

    private static void send(Waypoint w, String scope) {
        lastSent.put(w.id, signature(w, scope));
        PacketDistributor.sendToServer(new CmPayloads.ShareWaypoint(w.id, scope, w.name == null ? "" : w.name,
                w.dimension == null ? "" : w.dimension, w.x, w.y, w.z, w.color & 0xFFFFFF));
    }

    /** The edit screen saved a waypoint with this share choice. */
    public static void applyShare(Waypoint w, String scope) {
        if (!canShare(w)) {
            return;
        }
        String now = shareOf(w.id);
        if (scope.isEmpty() && now.isEmpty()) {
            return;
        }
        if (!scope.equals(now) || !signature(w, scope).equals(lastSent.get(w.id))) {
            Map<UUID, String> copy = new HashMap<>(myShares);
            if (scope.isEmpty()) {
                copy.remove(w.id);
            } else {
                copy.put(w.id, scope);
            }
            myShares = copy;
            send(w, scope);
        }
    }

    /** Diorama saved its waypoints: shared ones that changed (renamed, moved, recoloured) are sent again. */
    public static void syncMine(List<Waypoint> local) {
        if (myShares.isEmpty() || Minecraft.getInstance().getConnection() == null) {
            return;
        }
        for (Waypoint w : local) {
            String scope = w.id == null ? null : myShares.get(w.id);
            if (scope != null && !signature(w, scope).equals(lastSent.get(w.id))) {
                send(w, scope);
            }
        }
    }

    /** A waypoint is being deleted in Diorama: if it was shared, it stops being shared. */
    public static void onRemoved(Waypoint w) {
        if (w != null && w.id != null && myShares.containsKey(w.id) && Minecraft.getInstance().getConnection() != null) {
            Map<UUID, String> copy = new HashMap<>(myShares);
            copy.remove(w.id);
            myShares = copy;
            send(w, "");
        }
    }

    public static List<Waypoint> waypointsIn(String dimension) {
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : waypointObjects) {
            if (w.dimension.equals(dimension)) {
                out.add(w);
            }
        }
        return out;
    }

    public static List<Waypoint> allWaypoints() {
        return waypointObjects;
    }

    public static @Nullable CmPayloads.WaypointEntry shared(UUID id) {
        return id == null ? null : waypoints.get(id);
    }

    // ---------------------------------------------------------------- players

    public static void onPlayers(CmPayloads.Players payload) {
        Set<UUID> now = new HashSet<>();
        for (CmPayloads.PlayerEntry p : payload.players()) {
            now.add(p.id());
            CompoundTag tag = new CompoundTag();
            tag.putIntArray("i", UUIDUtil.uuidToIntArray(p.id()));
            tag.putDouble("x", p.x());
            tag.putDouble("y", p.y());
            tag.putDouble("z", p.z());
            tag.putString("d", p.dimension());
            TrackedPlayersInvoker.dioaddons$update(tag);
        }
        for (UUID gone : trackedPlayers) {
            if (!now.contains(gone)) {
                CompoundTag tag = new CompoundTag();
                tag.putIntArray("i", UUIDUtil.uuidToIntArray(gone));
                tag.putBoolean("r", true);
                TrackedPlayersInvoker.dioaddons$update(tag);
            }
        }
        trackedPlayers = now;
    }

    // ---------------------------------------------------------------- explored map

    /**
     * Hands a chunk received from the server to Diorama's map, which bakes and saves it like one it saw.
     * Returns false when there is no map session for this level yet.
     */
    public static boolean feed(ClientLevel level, LevelChunk chunk) {
        Minecraft mc = Minecraft.getInstance();
        MapSession session = MapSession.get();
        if (session == null || mc.player == null || mc.level != level) {
            return false;
        }
        MapLevel map = session.map();
        if (map.chunk(chunk.getPos().x, chunk.getPos().z) != null) {
            return true;
        }
        map.put(ChunkSnapshot.take(chunk, level), null, mc.player.chunkPosition().x, mc.player.chunkPosition().z);
        return true;
    }
}
