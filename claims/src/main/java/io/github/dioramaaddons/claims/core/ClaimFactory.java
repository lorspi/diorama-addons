package io.github.dioramaaddons.claims.core;

import java.util.Collection;
import java.util.EnumSet;
import java.util.UUID;

/** Builds a fully populated {@link Claim}, for storage loading. Use {@link ClaimStore#load(Claim)} to index it. */
public final class ClaimFactory {
    private ClaimFactory() {}

    public static Claim create(UUID id, String name, UUID owner, String ownerName, String dimension,
            Collection<Long> chunks, Collection<ClaimMember> members, EnumSet<Permission> publicPermissions) {
        Claim c = new Claim(id, name, owner, ownerName, dimension);
        c.mutableChunks().addAll(chunks);
        for (ClaimMember m : members) {
            if (!owner.equals(m.id())) {
                c.mutableMembers().put(m.id(), m);
            }
        }
        c.setPublicPermissions(publicPermissions);
        return c;
    }
}
