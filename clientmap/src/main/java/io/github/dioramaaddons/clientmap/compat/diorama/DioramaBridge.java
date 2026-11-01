package io.github.dioramaaddons.clientmap.compat.diorama;

import dev.diorama.Claims;
import dev.diorama.MapSession;
import dev.diorama.data.ChunkSnapshot;
import dev.diorama.render.MapLevel;
import dev.diorama.waypoint.Waypoint;
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

    private static Set<UUID> trackedPlayers = Set.of();

    private DioramaBridge() {}

    public static void clear() {
        CLAIMS.clear();
        CLAIM_INDEX.clear();
        claimsEnabled = false;
        waypoints = Map.of();
        waypointObjects = List.of();
        trackedPlayers = Set.of();
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
