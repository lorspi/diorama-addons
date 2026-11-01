package io.github.dioramaaddons.claims.core;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;

/** Quick presets for member permissions. */
public enum PermissionTemplate {
    VISITOR(EnumSet.of(Permission.INTERACT_BLOCKS)),
    MEMBER(EnumSet.of(Permission.INTERACT_BLOCKS, Permission.OPEN_CONTAINERS, Permission.USE_ITEMS,
            Permission.INTERACT_ENTITIES)),
    TRUSTED(Permission.playerPermissions());

    private final EnumSet<Permission> permissions;

    PermissionTemplate(EnumSet<Permission> permissions) {
        this.permissions = permissions;
    }

    public EnumSet<Permission> permissions() {
        return EnumSet.copyOf(permissions);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "dioaddons_claims.template." + id();
    }

    public static Optional<PermissionTemplate> byId(String id) {
        for (PermissionTemplate t : values()) {
            if (t.id().equalsIgnoreCase(id)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    /** Template whose permissions exactly match, if any. */
    public static Optional<PermissionTemplate> matching(EnumSet<Permission> perms) {
        for (PermissionTemplate t : values()) {
            if (t.permissions.equals(perms)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
