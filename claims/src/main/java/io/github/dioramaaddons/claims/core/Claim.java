package io.github.dioramaaddons.claims.core;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A named group of chunks in one dimension, owned by one player. Mutated only through {@link ClaimStore}
 * so the chunk index stays consistent.
 */
public final class Claim {
    private final UUID id;
    private String name;
    private UUID owner;
    private String ownerName;
    private final String dimension;
    private final Set<Long> chunks = new LinkedHashSet<>();
    private final Map<UUID, ClaimMember> members = new LinkedHashMap<>();
    private final EnumSet<Permission> publicPermissions = EnumSet.noneOf(Permission.class);

    public Claim(UUID id, String name, UUID owner, String ownerName, String dimension) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.ownerName = ownerName;
        this.dimension = dimension;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String dimension() {
        return dimension;
    }

    public Set<Long> chunks() {
        return Collections.unmodifiableSet(chunks);
    }

    Set<Long> mutableChunks() {
        return chunks;
    }

    public Collection<ClaimMember> members() {
        return Collections.unmodifiableCollection(members.values());
    }

    public Optional<ClaimMember> member(UUID player) {
        return Optional.ofNullable(members.get(player));
    }

    Map<UUID, ClaimMember> mutableMembers() {
        return members;
    }

    public EnumSet<Permission> publicPermissions() {
        return Permission.copyOf(publicPermissions);
    }

    void setPublicPermissions(EnumSet<Permission> perms) {
        publicPermissions.clear();
        publicPermissions.addAll(perms);
    }

    /** Claim-wide flag (scope {@link Permission.Scope#CLAIM}). */
    public boolean flag(Permission p) {
        return publicPermissions.contains(p);
    }

    public boolean isOwner(UUID player) {
        return owner.equals(player);
    }

    public boolean isMember(UUID player) {
        return members.containsKey(player);
    }
}
