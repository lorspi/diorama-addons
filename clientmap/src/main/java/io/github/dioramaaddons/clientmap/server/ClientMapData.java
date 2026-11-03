package io.github.dioramaaddons.clientmap.server;

import io.github.dioramaaddons.clientmap.core.ExploredLog;
import io.github.dioramaaddons.clientmap.core.GroupRegistry;
import io.github.dioramaaddons.clientmap.core.MapGroup;
import io.github.dioramaaddons.clientmap.core.SharedWaypoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Everything clientmap keeps on the server, in {@code data/dioaddons_clientmap.dat}: privacy choices, shared
 * waypoints by pool and explored chunks by pool and dimension. {@code format_version} allows migrations: version 2
 * adds the players' explored-map sharing choice and linked waypoints (shared from Diorama's waypoint screen).
 */
public final class ClientMapData extends SavedData {
    /** Kept from the former standalone mod, so existing worlds keep their data. */
    public static final String NAME = "dioaddons_clientmap";
    public static final int FORMAT_VERSION = 2;
    public static final SavedData.Factory<ClientMapData> FACTORY = new SavedData.Factory<>(ClientMapData::new, ClientMapData::load);

    /** Map groups of {@code /mapgroup}. */
    final GroupRegistry groups = new GroupRegistry();
    /** Players' own choice to show (true) or hide (false) their position; absent = server default. */
    final Map<UUID, Boolean> visibility = new HashMap<>();
    /** Players' own choice to share (true) or keep (false) what they explore; absent = shared. */
    final Map<UUID, Boolean> exploredSharing = new HashMap<>();
    final Map<String, List<SharedWaypoint>> waypoints = new LinkedHashMap<>();
    final Map<String, Map<String, ExploredLog>> explored = new HashMap<>();

    public ExploredLog explored(String scope, String dimension) {
        return explored.computeIfAbsent(scope, s -> new HashMap<>()).computeIfAbsent(dimension, d -> new ExploredLog());
    }

    public List<SharedWaypoint> waypoints(String scope) {
        return waypoints.computeIfAbsent(scope, s -> new ArrayList<>());
    }

    private static ClientMapData load(CompoundTag tag, HolderLookup.Provider registries) {
        ClientMapData d = new ClientMapData();
        int version = tag.contains("format_version") ? tag.getInt("format_version") : 1;
        if (version > FORMAT_VERSION) {
            throw new IllegalStateException("clientmap data format " + version + " is newer than supported " + FORMAT_VERSION);
        }
        for (Tag t : tag.getList("groups", Tag.TAG_COMPOUND)) {
            CompoundTag g = (CompoundTag) t;
            MapGroup group = new MapGroup(g.getUUID("id"), g.getString("name"), g.getUUID("owner"));
            for (Tag m : g.getList("members", Tag.TAG_COMPOUND)) {
                CompoundTag mc = (CompoundTag) m;
                group.members().put(mc.getUUID("id"), mc.getString("name"));
            }
            for (Tag i : g.getList("invites", Tag.TAG_STRING)) {
                group.invites().add(UUID.fromString(i.getAsString()));
            }
            if (!group.members().isEmpty()) {
                d.groups.load(group);
            }
        }
        CompoundTag vis = tag.getCompound("visibility");
        for (String key : vis.getAllKeys()) {
            d.visibility.put(UUID.fromString(key), vis.getBoolean(key));
        }
        CompoundTag exs = tag.getCompound("explored_sharing");
        for (String key : exs.getAllKeys()) {
            d.exploredSharing.put(UUID.fromString(key), exs.getBoolean(key));
        }
        CompoundTag wps = tag.getCompound("waypoints");
        for (String scope : wps.getAllKeys()) {
            List<SharedWaypoint> list = d.waypoints(scope);
            for (Tag t : wps.getList(scope, Tag.TAG_COMPOUND)) {
                CompoundTag w = (CompoundTag) t;
                list.add(new SharedWaypoint(w.getUUID("id"), w.getString("name"), w.getString("dimension"), w.getInt("x"),
                        w.getInt("y"), w.getInt("z"), w.getInt("color"), w.getUUID("owner"), w.getString("owner_name"), w.getBoolean("linked")));
            }
        }
        CompoundTag exp = tag.getCompound("explored");
        for (String scope : exp.getAllKeys()) {
            CompoundTag dims = exp.getCompound(scope);
            for (String dim : dims.getAllKeys()) {
                d.explored.computeIfAbsent(scope, s -> new HashMap<>()).put(dim, ExploredLog.of(dims.getLongArray(dim)));
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format_version", FORMAT_VERSION);
        ListTag groupList = new ListTag();
        for (MapGroup g : groups.all()) {
            CompoundTag gt = new CompoundTag();
            gt.putUUID("id", g.id());
            gt.putString("name", g.name());
            gt.putUUID("owner", g.owner());
            ListTag members = new ListTag();
            g.members().forEach((id, name) -> {
                CompoundTag m = new CompoundTag();
                m.putUUID("id", id);
                m.putString("name", name);
                members.add(m);
            });
            gt.put("members", members);
            ListTag invites = new ListTag();
            g.invites().forEach(i -> invites.add(StringTag.valueOf(i.toString())));
            gt.put("invites", invites);
            groupList.add(gt);
        }
        tag.put("groups", groupList);
        CompoundTag vis = new CompoundTag();
        visibility.forEach((id, v) -> vis.putBoolean(id.toString(), v));
        tag.put("visibility", vis);
        CompoundTag exs = new CompoundTag();
        exploredSharing.forEach((id, v) -> exs.putBoolean(id.toString(), v));
        tag.put("explored_sharing", exs);
        CompoundTag wps = new CompoundTag();
        waypoints.forEach((scope, list) -> {
            ListTag l = new ListTag();
            for (SharedWaypoint w : list) {
                CompoundTag wt = new CompoundTag();
                wt.putUUID("id", w.id());
                wt.putString("name", w.name());
                wt.putString("dimension", w.dimension());
                wt.putInt("x", w.x());
                wt.putInt("y", w.y());
                wt.putInt("z", w.z());
                wt.putInt("color", w.color());
                wt.putUUID("owner", w.owner());
                wt.putString("owner_name", w.ownerName());
                if (w.linked()) {
                    wt.putBoolean("linked", true);
                }
                l.add(wt);
            }
            wps.put(scope, l);
        });
        tag.put("waypoints", wps);
        CompoundTag exp = new CompoundTag();
        explored.forEach((scope, dims) -> {
            CompoundTag dt = new CompoundTag();
            dims.forEach((dim, log) -> dt.put(dim, new LongArrayTag(log.toArray())));
            exp.put(scope, dt);
        });
        tag.put("explored", exp);
        return tag;
    }
}
