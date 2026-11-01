package io.github.dioramaaddons.claims.core;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Resolution order: owner (everything) → member permissions → public permissions. A member never has fewer
 * rights than the public: their effective set is the union of both. Claim-scoped permissions are flags that
 * only depend on the public set.
 */
public final class PermissionResolver {
    private PermissionResolver() {}

    public static boolean has(Claim claim, UUID player, Permission permission) {
        if (permission.scope() == Permission.Scope.CLAIM) {
            return claim.flag(permission);
        }
        if (player != null && claim.isOwner(player)) {
            return true;
        }
        if (player != null) {
            ClaimMember member = claim.member(player).orElse(null);
            if (member != null && member.has(permission)) {
                return true;
            }
        }
        return claim.publicPermissions().contains(permission);
    }

    /** Effective player-scoped permissions of a player in a claim. */
    public static EnumSet<Permission> effective(Claim claim, UUID player) {
        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        for (Permission p : Permission.playerPermissions()) {
            if (has(claim, player, p)) {
                result.add(p);
            }
        }
        return result;
    }
}
