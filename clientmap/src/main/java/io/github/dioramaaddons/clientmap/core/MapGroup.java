package io.github.dioramaaddons.clientmap.core;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A group of friends sharing explored map and locations when the server uses {@link ShareMode#GROUPS}. */
public final class MapGroup {
    private final UUID id;
    private String name;
    private UUID owner;
    /** Members, owner included, with their last known names. */
    private final Map<UUID, String> members = new LinkedHashMap<>();
    private final Set<UUID> invites = new LinkedHashSet<>();

    public MapGroup(UUID id, String name, UUID owner) {
        this.id = id;
        this.name = name;
        this.owner = owner;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public UUID owner() {
        return owner;
    }

    void setOwner(UUID owner) {
        this.owner = owner;
    }

    public Map<UUID, String> members() {
        return members;
    }

    public Set<UUID> invites() {
        return invites;
    }
}
