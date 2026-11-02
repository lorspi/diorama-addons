package io.github.dioramaaddons.claims.core;

import io.github.dioramaaddons.claimsapi.ChunkCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.Nullable;

/**
 * All claims plus a per-dimension chunk index. Contains every rule (limits, contiguity, ownership) and no
 * Minecraft types, so it is unit-testable. Not thread-safe: use it from the server thread only.
 */
public final class ClaimStore {
    public static final int MAX_NAME_LENGTH = 32;

    public enum ChangeType { CREATED, UPDATED, REMOVED }

    @FunctionalInterface
    public interface Listener {
        void changed(ChangeType type, Claim claim);
    }

    /**
     * Who performs an action.
     *
     * @param admin true when the actor is an operator with protection bypass enabled: may manage any claim
     *              and ignores limits
     */
    public record Actor(UUID id, String name, boolean admin) {}

    public record Outcome(ClaimResult result, @Nullable Claim claim) {
        static Outcome of(ClaimResult result) {
            return new Outcome(result, null);
        }
    }

    /**
     * @param changed    chunks that were claimed or released
     * @param firstError first failure seen, or {@link ClaimResult#SUCCESS}
     * @param claim      claim affected by the last successful change
     */
    public record BatchOutcome(int changed, ClaimResult firstError, @Nullable Claim claim) {}

    private final Map<UUID, Claim> claims = new LinkedHashMap<>();
    private final Map<String, Map<Long, UUID>> index = new HashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private EnumSet<Permission> defaultPublicPermissions = EnumSet.noneOf(Permission.class);

    /** Public permissions given to claims created from now on. */
    public void setDefaultPublicPermissions(EnumSet<Permission> perms) {
        defaultPublicPermissions = Permission.copyOf(perms);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    private void fire(ChangeType type, Claim claim) {
        for (Listener l : listeners) {
            l.changed(type, claim);
        }
    }

    // ---------------------------------------------------------------- queries

    public Optional<Claim> claim(UUID id) {
        return Optional.ofNullable(claims.get(id));
    }

    public @Nullable Claim claimAt(String dimension, long chunk) {
        Map<Long, UUID> dim = index.get(dimension);
        if (dim == null) {
            return null;
        }
        UUID id = dim.get(chunk);
        return id == null ? null : claims.get(id);
    }

    public Collection<Claim> all() {
        return Collections.unmodifiableCollection(claims.values());
    }

    public List<Claim> claimsOf(UUID owner) {
        List<Claim> out = new ArrayList<>();
        for (Claim c : claims.values()) {
            if (c.isOwner(owner)) {
                out.add(c);
            }
        }
        return out;
    }

    public List<Claim> claimsIn(String dimension) {
        List<Claim> out = new ArrayList<>();
        for (Claim c : claims.values()) {
            if (c.dimension().equals(dimension)) {
                out.add(c);
            }
        }
        return out;
    }

    public int chunkCount(UUID owner) {
        int n = 0;
        for (Claim c : claims.values()) {
            if (c.isOwner(owner)) {
                n += c.chunks().size();
            }
        }
        return n;
    }

    public static boolean canManage(Actor actor, Claim claim) {
        return actor.admin() || claim.isOwner(actor.id());
    }

    public static boolean isValidName(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < 0x20 || c == '§' || c == 0x7f) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- claiming

    /**
     * Claims one chunk.
     *
     * @param target       claim to extend; when null the chunk joins an adjacent claim of the actor in the same
     *                     dimension, or a new claim is created
     * @param newClaimName name for a new claim; when null a default name is generated
     * @param spawnChunk   packed spawn chunk when the dimension has a protected spawn, otherwise null
     */
    public Outcome claimChunk(Actor actor, String dimension, long chunk, @Nullable UUID target,
            @Nullable String newClaimName, ClaimLimits limits, @Nullable Long spawnChunk) {
        Claim targetClaim = null;
        if (target != null) {
            targetClaim = claims.get(target);
            if (targetClaim == null) {
                return Outcome.of(ClaimResult.CLAIM_NOT_FOUND);
            }
            if (!canManage(actor, targetClaim)) {
                return Outcome.of(ClaimResult.NOT_OWNER);
            }
            if (!targetClaim.dimension().equals(dimension)) {
                return Outcome.of(ClaimResult.WRONG_DIMENSION);
            }
        }
        UUID owner = targetClaim != null ? targetClaim.owner() : actor.id();

        Claim existing = claimAt(dimension, chunk);
        if (existing != null) {
            return Outcome.of(existing.isOwner(owner) ? ClaimResult.ALREADY_YOURS : ClaimResult.ALREADY_CLAIMED);
        }

        if (!actor.admin() && limits.minSpawnDistance() > 0 && spawnChunk != null
                && chebyshev(chunk, spawnChunk) < limits.minSpawnDistance()) {
            return Outcome.of(ClaimResult.TOO_CLOSE_TO_SPAWN);
        }
        if (!actor.admin() && chunkCount(owner) >= limits.maxChunksPerPlayer()) {
            return Outcome.of(ClaimResult.CHUNK_LIMIT);
        }

        if (targetClaim == null) {
            for (Claim c : claims.values()) {
                if (c.isOwner(owner) && c.dimension().equals(dimension) && isAdjacent(c, chunk)) {
                    targetClaim = c;
                    break;
                }
            }
        }

        if (targetClaim != null) {
            if (limits.requireContiguous() && !targetClaim.chunks().isEmpty() && !isAdjacent(targetClaim, chunk)) {
                return Outcome.of(ClaimResult.NOT_CONTIGUOUS);
            }
            targetClaim.mutableChunks().add(chunk);
            index.computeIfAbsent(dimension, d -> new HashMap<>()).put(chunk, targetClaim.id());
            fire(ChangeType.UPDATED, targetClaim);
            return new Outcome(ClaimResult.SUCCESS, targetClaim);
        }

        if (!actor.admin() && claimsOf(owner).size() >= limits.maxClaimsPerPlayer()) {
            return Outcome.of(ClaimResult.CLAIM_LIMIT);
        }
        String name = newClaimName != null && isValidName(newClaimName) ? newClaimName.trim() : defaultName(owner);
        Claim created = new Claim(UUID.randomUUID(), name, owner, actor.name(), dimension);
        created.mutableChunks().add(chunk);
        created.setPublicPermissions(defaultPublicPermissions);
        claims.put(created.id(), created);
        index.computeIfAbsent(dimension, d -> new HashMap<>()).put(chunk, created.id());
        fire(ChangeType.CREATED, created);
        return new Outcome(ClaimResult.SUCCESS, created);
    }

    /**
     * Claims several chunks, retrying the ones that are not yet contiguous so order does not matter. When
     * {@code target} is null, every chunk joins the claim chosen for the first successful chunk.
     */
    public BatchOutcome claimChunks(Actor actor, String dimension, Collection<Long> chunks, @Nullable UUID target,
            ClaimLimits limits, @Nullable Long spawnChunk) {
        Set<Long> remaining = new LinkedHashSet<>(chunks);
        int changed = 0;
        ClaimResult firstError = ClaimResult.SUCCESS;
        Claim last = null;
        UUID currentTarget = target;
        boolean progress = true;
        while (progress && !remaining.isEmpty()) {
            progress = false;
            ClaimResult passError = ClaimResult.SUCCESS;
            for (var it = remaining.iterator(); it.hasNext(); ) {
                long chunk = it.next();
                Outcome o = claimChunk(actor, dimension, chunk, currentTarget, null, limits, spawnChunk);
                if (o.result().ok()) {
                    it.remove();
                    changed++;
                    progress = true;
                    last = o.claim();
                    if (currentTarget == null && last != null) {
                        currentTarget = last.id();
                    }
                } else if (o.result() != ClaimResult.NOT_CONTIGUOUS) {
                    it.remove();
                    if (firstError.ok()) {
                        firstError = o.result();
                    }
                } else if (passError.ok()) {
                    passError = o.result();
                }
            }
            if (!progress && firstError.ok()) {
                firstError = passError;
            }
        }
        return new BatchOutcome(changed, firstError, last);
    }

    public Outcome unclaimChunk(Actor actor, String dimension, long chunk, ClaimLimits limits) {
        Claim claim = claimAt(dimension, chunk);
        if (claim == null) {
            return Outcome.of(ClaimResult.NOT_CLAIMED);
        }
        if (!canManage(actor, claim)) {
            return Outcome.of(ClaimResult.NOT_OWNER);
        }
        if (limits.requireContiguous() && wouldSplit(claim, chunk)) {
            return Outcome.of(ClaimResult.WOULD_SPLIT);
        }
        claim.mutableChunks().remove(chunk);
        Map<Long, UUID> dim = index.get(dimension);
        if (dim != null) {
            dim.remove(chunk);
        }
        if (claim.chunks().isEmpty()) {
            claims.remove(claim.id());
            fire(ChangeType.REMOVED, claim);
        } else {
            fire(ChangeType.UPDATED, claim);
        }
        return new Outcome(ClaimResult.SUCCESS, claim);
    }

    /** Releases several chunks, retrying the ones that would split a claim until no progress is made. */
    public BatchOutcome unclaimChunks(Actor actor, String dimension, Collection<Long> chunks, ClaimLimits limits) {
        Set<Long> remaining = new LinkedHashSet<>(chunks);
        int changed = 0;
        ClaimResult firstError = ClaimResult.SUCCESS;
        Claim last = null;
        boolean progress = true;
        while (progress && !remaining.isEmpty()) {
            progress = false;
            ClaimResult passError = ClaimResult.SUCCESS;
            for (var it = remaining.iterator(); it.hasNext(); ) {
                long chunk = it.next();
                Outcome o = unclaimChunk(actor, dimension, chunk, limits);
                if (o.result().ok()) {
                    it.remove();
                    changed++;
                    progress = true;
                    last = o.claim();
                } else if (o.result() != ClaimResult.WOULD_SPLIT) {
                    it.remove();
                    if (firstError.ok()) {
                        firstError = o.result();
                    }
                } else if (passError.ok()) {
                    passError = o.result();
                }
            }
            if (!progress && firstError.ok()) {
                firstError = passError;
            }
        }
        return new BatchOutcome(changed, firstError, last);
    }

    // ---------------------------------------------------------------- editing

    private Outcome editable(Actor actor, UUID claimId) {
        Claim claim = claims.get(claimId);
        if (claim == null) {
            return Outcome.of(ClaimResult.CLAIM_NOT_FOUND);
        }
        if (!canManage(actor, claim)) {
            return Outcome.of(ClaimResult.NOT_OWNER);
        }
        return new Outcome(ClaimResult.SUCCESS, claim);
    }

    public Outcome rename(Actor actor, UUID claimId, String name) {
        Outcome o = editable(actor, claimId);
        if (!o.result().ok()) {
            return o;
        }
        if (!isValidName(name)) {
            return Outcome.of(ClaimResult.INVALID_NAME);
        }
        o.claim().setName(name.trim());
        fire(ChangeType.UPDATED, o.claim());
        return o;
    }

    public Outcome delete(Actor actor, UUID claimId) {
        Outcome o = editable(actor, claimId);
        if (!o.result().ok()) {
            return o;
        }
        Claim claim = o.claim();
        claims.remove(claimId);
        Map<Long, UUID> dim = index.get(claim.dimension());
        if (dim != null) {
            for (long c : claim.chunks()) {
                dim.remove(c);
            }
        }
        fire(ChangeType.REMOVED, claim);
        return o;
    }

    /** Adds a member or replaces the permissions of an existing one. */
    public Outcome setMember(Actor actor, UUID claimId, UUID memberId, String memberName, EnumSet<Permission> perms,
            ClaimLimits limits) {
        Outcome o = editable(actor, claimId);
        if (!o.result().ok()) {
            return o;
        }
        Claim claim = o.claim();
        if (claim.isOwner(memberId)) {
            return Outcome.of(ClaimResult.CANNOT_ADD_OWNER);
        }
        ClaimMember existing = claim.mutableMembers().get(memberId);
        if (existing == null) {
            if (!actor.admin() && claim.members().size() >= limits.maxMembersPerClaim()) {
                return Outcome.of(ClaimResult.MEMBER_LIMIT);
            }
            claim.mutableMembers().put(memberId, new ClaimMember(memberId, memberName, perms));
        } else {
            existing.setPermissions(perms);
            if (memberName != null && !memberName.isEmpty()) {
                existing.setName(memberName);
            }
        }
        fire(ChangeType.UPDATED, claim);
        return o;
    }

    public Outcome removeMember(Actor actor, UUID claimId, UUID memberId) {
        Outcome o = editable(actor, claimId);
        if (!o.result().ok()) {
            return o;
        }
        if (o.claim().mutableMembers().remove(memberId) == null) {
            return Outcome.of(ClaimResult.MEMBER_NOT_FOUND);
        }
        fire(ChangeType.UPDATED, o.claim());
        return o;
    }

    public Outcome setPublicPermissions(Actor actor, UUID claimId, EnumSet<Permission> perms) {
        Outcome o = editable(actor, claimId);
        if (!o.result().ok()) {
            return o;
        }
        o.claim().setPublicPermissions(perms);
        fire(ChangeType.UPDATED, o.claim());
        return o;
    }

    /**
     * Gives a claim to another player. Operators only (admin actor); the new owner's limits are not checked.
     * When the new owner was a member, their member entry is dropped, since owners have every permission.
     */
    public Outcome transfer(Actor actor, UUID claimId, UUID newOwner, String newOwnerName) {
        Claim claim = claims.get(claimId);
        if (claim == null) {
            return Outcome.of(ClaimResult.CLAIM_NOT_FOUND);
        }
        if (!actor.admin()) {
            return Outcome.of(ClaimResult.NOT_OWNER);
        }
        if (claim.isOwner(newOwner)) {
            return Outcome.of(ClaimResult.ALREADY_OWNER);
        }
        claim.mutableMembers().remove(newOwner);
        claim.setOwner(newOwner, newOwnerName);
        fire(ChangeType.UPDATED, claim);
        return new Outcome(ClaimResult.SUCCESS, claim);
    }

    /** Keeps stored player names current. Returns true when anything changed. */
    public boolean updatePlayerName(UUID player, String name) {
        boolean changed = false;
        for (Claim c : claims.values()) {
            if (c.isOwner(player) && !name.equals(c.ownerName())) {
                c.setOwnerName(name);
                changed = true;
                fire(ChangeType.UPDATED, c);
            }
            ClaimMember m = c.mutableMembers().get(player);
            if (m != null && !name.equals(m.name())) {
                m.setName(name);
                changed = true;
            }
        }
        return changed;
    }

    // ---------------------------------------------------------------- loading

    /** Adds a claim read from storage. Chunks already indexed by another claim are dropped. */
    public void load(Claim claim) {
        Map<Long, UUID> dim = index.computeIfAbsent(claim.dimension(), d -> new HashMap<>());
        claim.mutableChunks().removeIf(c -> dim.containsKey(c));
        if (claim.chunks().isEmpty()) {
            return;
        }
        claims.put(claim.id(), claim);
        for (long c : claim.chunks()) {
            dim.put(c, claim.id());
        }
    }

    // ---------------------------------------------------------------- geometry

    private String defaultName(UUID owner) {
        Set<String> used = new HashSet<>();
        for (Claim c : claimsOf(owner)) {
            used.add(c.name());
        }
        int n = 1;
        while (used.contains("Claim " + n)) {
            n++;
        }
        return "Claim " + n;
    }

    static int chebyshev(long a, long b) {
        int dx = Math.abs(ChunkCoord.unpackX(a) - ChunkCoord.unpackX(b));
        int dz = Math.abs(ChunkCoord.unpackZ(a) - ChunkCoord.unpackZ(b));
        return Math.max(dx, dz);
    }

    private static long[] neighbours(long chunk) {
        int x = ChunkCoord.unpackX(chunk);
        int z = ChunkCoord.unpackZ(chunk);
        return new long[] {
            ChunkCoord.pack(x + 1, z), ChunkCoord.pack(x - 1, z), ChunkCoord.pack(x, z + 1), ChunkCoord.pack(x, z - 1)
        };
    }

    static boolean isAdjacent(Claim claim, long chunk) {
        for (long n : neighbours(chunk)) {
            if (claim.chunks().contains(n)) {
                return true;
            }
        }
        return false;
    }

    /** True when removing {@code chunk} leaves the rest of the claim in more than one piece. */
    static boolean wouldSplit(Claim claim, long chunk) {
        int remaining = claim.chunks().size() - 1;
        if (remaining <= 1) {
            return false;
        }
        Long start = null;
        for (long c : claim.chunks()) {
            if (c != chunk) {
                start = c;
                break;
            }
        }
        Set<Long> seen = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            long cur = queue.poll();
            for (long n : neighbours(cur)) {
                if (n != chunk && claim.chunks().contains(n) && seen.add(n)) {
                    queue.add(n);
                }
            }
        }
        return seen.size() < remaining;
    }
}
