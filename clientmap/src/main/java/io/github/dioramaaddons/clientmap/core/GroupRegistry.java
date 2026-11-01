package io.github.dioramaaddons.clientmap.core;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Groups of friends. One group per player. Every rule is here; no Minecraft types. */
public final class GroupRegistry {
    public static final int MAX_NAME = 24;

    public enum Result {
        SUCCESS, INVALID_NAME, NAME_TAKEN, ALREADY_IN_GROUP, NOT_IN_GROUP, NOT_OWNER, NOT_INVITED, NO_SUCH_GROUP,
        NOT_A_MEMBER, CANNOT_KICK_SELF;

        public String translationKey() {
            return "dioaddons_clientmap.group." + name().toLowerCase(Locale.ROOT);
        }
    }

    private final Map<UUID, MapGroup> groups = new LinkedHashMap<>();
    private final Map<UUID, UUID> groupOf = new HashMap<>();

    public Collection<MapGroup> all() {
        return Collections.unmodifiableCollection(groups.values());
    }

    public Optional<MapGroup> groupOf(UUID player) {
        UUID id = groupOf.get(player);
        return id == null ? Optional.empty() : Optional.ofNullable(groups.get(id));
    }

    public @Nullable UUID groupIdOf(UUID player) {
        return groupOf.get(player);
    }

    public Optional<MapGroup> byName(String name) {
        for (MapGroup g : groups.values()) {
            if (g.name().equalsIgnoreCase(name)) {
                return Optional.of(g);
            }
        }
        return Optional.empty();
    }

    public static boolean validName(String name) {
        return name != null && name.length() >= 1 && name.length() <= MAX_NAME && name.matches("[A-Za-z0-9_\\- ]+")
                && !name.isBlank();
    }

    public Result create(UUID owner, String ownerName, String name) {
        if (!validName(name)) {
            return Result.INVALID_NAME;
        }
        if (groupOf.containsKey(owner)) {
            return Result.ALREADY_IN_GROUP;
        }
        if (byName(name).isPresent()) {
            return Result.NAME_TAKEN;
        }
        MapGroup g = new MapGroup(UUID.randomUUID(), name.trim(), owner);
        g.members().put(owner, ownerName);
        groups.put(g.id(), g);
        groupOf.put(owner, g.id());
        return Result.SUCCESS;
    }

    public Result invite(UUID actor, UUID target) {
        MapGroup g = groupOf(actor).orElse(null);
        if (g == null) {
            return Result.NOT_IN_GROUP;
        }
        if (!g.owner().equals(actor)) {
            return Result.NOT_OWNER;
        }
        if (groupOf.containsKey(target)) {
            return Result.ALREADY_IN_GROUP;
        }
        g.invites().add(target);
        return Result.SUCCESS;
    }

    public Result join(UUID player, String playerName, String groupName) {
        if (groupOf.containsKey(player)) {
            return Result.ALREADY_IN_GROUP;
        }
        MapGroup g = byName(groupName).orElse(null);
        if (g == null) {
            return Result.NO_SUCH_GROUP;
        }
        if (!g.invites().remove(player)) {
            return Result.NOT_INVITED;
        }
        g.members().put(player, playerName);
        groupOf.put(player, g.id());
        return Result.SUCCESS;
    }

    /** Leaves the group. The owner leaving hands it to the next member, or the group is deleted when empty. */
    public Result leave(UUID player) {
        MapGroup g = groupOf(player).orElse(null);
        if (g == null) {
            return Result.NOT_IN_GROUP;
        }
        g.members().remove(player);
        groupOf.remove(player);
        if (g.members().isEmpty()) {
            groups.remove(g.id());
        } else if (g.owner().equals(player)) {
            g.setOwner(g.members().keySet().iterator().next());
        }
        return Result.SUCCESS;
    }

    public Result kick(UUID actor, UUID target) {
        MapGroup g = groupOf(actor).orElse(null);
        if (g == null) {
            return Result.NOT_IN_GROUP;
        }
        if (!g.owner().equals(actor)) {
            return Result.NOT_OWNER;
        }
        if (actor.equals(target)) {
            return Result.CANNOT_KICK_SELF;
        }
        if (!g.members().containsKey(target)) {
            return Result.NOT_A_MEMBER;
        }
        return leave(target);
    }

    /** For loading. */
    public void load(MapGroup group) {
        groups.put(group.id(), group);
        for (UUID m : group.members().keySet()) {
            groupOf.put(m, group.id());
        }
    }
}
