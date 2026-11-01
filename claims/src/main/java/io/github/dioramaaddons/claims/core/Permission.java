package io.github.dioramaaddons.claims.core;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Claim permissions.
 *
 * <p>{@link Scope#PLAYER} permissions are granted to players (members or the public).
 * {@link Scope#CLAIM} permissions are claim-wide flags that apply to everything happening inside the
 * claim (they can only be set on the public permission set).
 */
public enum Permission {
    BREAK_BLOCKS(Scope.PLAYER),
    PLACE_BLOCKS(Scope.PLAYER),
    INTERACT_BLOCKS(Scope.PLAYER),
    OPEN_CONTAINERS(Scope.PLAYER),
    USE_ITEMS(Scope.PLAYER),
    INTERACT_ENTITIES(Scope.PLAYER),
    DAMAGE_ANIMALS(Scope.PLAYER),
    TRAMPLE_FARMLAND(Scope.PLAYER),
    PVP(Scope.CLAIM),
    EXPLOSIONS(Scope.CLAIM),
    FIRE_SPREAD(Scope.CLAIM),
    MOB_GRIEFING(Scope.CLAIM);

    public enum Scope { PLAYER, CLAIM }

    private final Scope scope;

    Permission(Scope scope) {
        this.scope = scope;
    }

    public Scope scope() {
        return scope;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "dioaddons_claims.permission." + id();
    }

    public int bit() {
        return 1 << ordinal();
    }

    public static Optional<Permission> byId(String id) {
        for (Permission p : values()) {
            if (p.id().equalsIgnoreCase(id)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    public static EnumSet<Permission> playerPermissions() {
        EnumSet<Permission> set = EnumSet.noneOf(Permission.class);
        for (Permission p : values()) {
            if (p.scope == Scope.PLAYER) {
                set.add(p);
            }
        }
        return set;
    }

    public static int toMask(Collection<Permission> perms) {
        int mask = 0;
        for (Permission p : perms) {
            mask |= p.bit();
        }
        return mask;
    }

    /** Unknown bits are ignored. */
    public static EnumSet<Permission> fromMask(int mask) {
        EnumSet<Permission> set = EnumSet.noneOf(Permission.class);
        for (Permission p : values()) {
            if ((mask & p.bit()) != 0) {
                set.add(p);
            }
        }
        return set;
    }

    public static EnumSet<Permission> copyOf(Set<Permission> perms) {
        return perms.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(perms);
    }
}
