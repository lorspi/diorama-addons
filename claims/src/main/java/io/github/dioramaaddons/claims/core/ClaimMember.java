package io.github.dioramaaddons.claims.core;

import java.util.EnumSet;
import java.util.UUID;

/** A friend added to a claim, with their own player permissions. */
public final class ClaimMember {
    private final UUID id;
    private String name;
    private final EnumSet<Permission> permissions;

    public ClaimMember(UUID id, String name, EnumSet<Permission> permissions) {
        this.id = id;
        this.name = name;
        this.permissions = EnumSet.noneOf(Permission.class);
        setPermissions(permissions);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public EnumSet<Permission> permissions() {
        return EnumSet.copyOf(permissions.isEmpty() ? EnumSet.noneOf(Permission.class) : permissions);
    }

    /** Only {@link Permission.Scope#PLAYER} permissions are kept. */
    public void setPermissions(EnumSet<Permission> perms) {
        permissions.clear();
        for (Permission p : perms) {
            if (p.scope() == Permission.Scope.PLAYER) {
                permissions.add(p);
            }
        }
    }

    public boolean has(Permission p) {
        return permissions.contains(p);
    }
}
