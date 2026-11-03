package io.github.dioramaaddons.claims.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.dioramaaddons.claims.core.Claim;
import io.github.dioramaaddons.claims.core.ClaimFactory;
import io.github.dioramaaddons.claims.core.ClaimMember;
import io.github.dioramaaddons.claims.core.ClaimSettings;
import io.github.dioramaaddons.claims.core.PlayerLimits;
import io.github.dioramaaddons.claims.core.ClaimStore;
import io.github.dioramaaddons.claims.core.Permission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Versioned codecs for claim data. Only DataFixerUpper types are used, so this is testable without
 * bootstrapping Minecraft.
 *
 * <p>Root layout: {@code {format_version: int, claims: [...], player_limits: [...]}}. Version 2 added each claim's
 * {@code settings} (greetings, visitor alarm) and the players' own limits; both are optional, so version 1 data reads
 * as is. Bump {@link #FORMAT_VERSION} and add a step to
 * {@link #migrate(Dynamic)} whenever the layout changes.
 */
public final class ClaimCodecs {
    public static final int FORMAT_VERSION = 2;

    private ClaimCodecs() {}

    public static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(s -> {
        try {
            return DataResult.success(UUID.fromString(s));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "Invalid UUID: " + s);
        }
    }, UUID::toString);

    /** Stored by name; unknown names are ignored so removing a permission does not break old data. */
    public static final Codec<EnumSet<Permission>> PERMISSIONS = Codec.STRING.listOf().xmap(names -> {
        EnumSet<Permission> set = EnumSet.noneOf(Permission.class);
        for (String n : names) {
            Permission.byId(n).ifPresent(set::add);
        }
        return set;
    }, set -> set.stream().map(Permission::id).toList());

    public static final Codec<ClaimMember> MEMBER = RecordCodecBuilder.create(i -> i.group(
            UUID_CODEC.fieldOf("id").forGetter(ClaimMember::id),
            Codec.STRING.optionalFieldOf("name", "").forGetter(ClaimMember::name),
            PERMISSIONS.optionalFieldOf("permissions", EnumSet.noneOf(Permission.class))
                    .forGetter(ClaimMember::permissions))
            .apply(i, ClaimMember::new));

    public static final Codec<ClaimSettings> SETTINGS = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("welcome_title", "").forGetter(ClaimSettings::welcomeTitle),
            Codec.STRING.optionalFieldOf("welcome", "").forGetter(ClaimSettings::welcome),
            Codec.STRING.optionalFieldOf("farewell_title", "").forGetter(ClaimSettings::farewellTitle),
            Codec.STRING.optionalFieldOf("farewell", "").forGetter(ClaimSettings::farewell),
            Codec.BOOL.optionalFieldOf("alarm_owner", false).forGetter(ClaimSettings::alarmOwner),
            Codec.BOOL.optionalFieldOf("alarm_members", false).forGetter(ClaimSettings::alarmMembers))
            .apply(i, ClaimSettings::new));

    /** One player's own limits; absent values use the server limits. */
    record StoredLimits(UUID player, Optional<Integer> chunks, Optional<Integer> claims, Optional<Integer> members) {}

    static final Codec<StoredLimits> LIMITS = RecordCodecBuilder.create(i -> i.group(
            UUID_CODEC.fieldOf("player").forGetter(StoredLimits::player),
            Codec.INT.optionalFieldOf("chunks").forGetter(StoredLimits::chunks),
            Codec.INT.optionalFieldOf("claims").forGetter(StoredLimits::claims),
            Codec.INT.optionalFieldOf("members").forGetter(StoredLimits::members))
            .apply(i, StoredLimits::new));

    /** Intermediate form, because {@link Claim} is mutable and built through {@link ClaimStore}. */
    record StoredClaim(UUID id, String name, UUID owner, String ownerName, String dimension, List<Long> chunks,
            List<ClaimMember> members, EnumSet<Permission> publicPermissions, ClaimSettings settings) {
        static StoredClaim of(Claim c) {
            return new StoredClaim(c.id(), c.name(), c.owner(), c.ownerName(), c.dimension(),
                    new ArrayList<>(c.chunks()), new ArrayList<>(c.members()), c.publicPermissions(), c.settings());
        }

        Claim toClaim() {
            return ClaimFactory.create(id, name, owner, ownerName, dimension, chunks, members, publicPermissions,
                    settings);
        }
    }

    static final Codec<StoredClaim> CLAIM = RecordCodecBuilder.create(i -> i.group(
            UUID_CODEC.fieldOf("id").forGetter(StoredClaim::id),
            Codec.STRING.fieldOf("name").forGetter(StoredClaim::name),
            UUID_CODEC.fieldOf("owner").forGetter(StoredClaim::owner),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(StoredClaim::ownerName),
            Codec.STRING.fieldOf("dimension").forGetter(StoredClaim::dimension),
            Codec.LONG.listOf().fieldOf("chunks").forGetter(StoredClaim::chunks),
            MEMBER.listOf().optionalFieldOf("members", List.of()).forGetter(StoredClaim::members),
            PERMISSIONS.optionalFieldOf("public", EnumSet.noneOf(Permission.class))
                    .forGetter(StoredClaim::publicPermissions),
            SETTINGS.optionalFieldOf("settings", ClaimSettings.DEFAULT).forGetter(StoredClaim::settings))
            .apply(i, StoredClaim::new));

    record Root(int formatVersion, List<StoredClaim> claims, List<StoredLimits> playerLimits) {}

    static final Codec<Root> ROOT = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("format_version").forGetter(Root::formatVersion),
            CLAIM.listOf().fieldOf("claims").forGetter(Root::claims),
            LIMITS.listOf().optionalFieldOf("player_limits", List.of()).forGetter(Root::playerLimits))
            .apply(i, Root::new));

    public static <T> DataResult<T> encode(ClaimStore store, DynamicOps<T> ops) {
        List<StoredClaim> list = store.all().stream().map(StoredClaim::of).toList();
        List<StoredLimits> limits = new ArrayList<>();
        store.playerLimits().forEach((player, l) -> limits.add(new StoredLimits(player, l.get(PlayerLimits.Key.CHUNKS),
                l.get(PlayerLimits.Key.CLAIMS), l.get(PlayerLimits.Key.MEMBERS))));
        return ROOT.encodeStart(ops, new Root(FORMAT_VERSION, list, limits));
    }

    /** Decodes into {@code store}. Claims that fail to decode are skipped and reported through the result. */
    public static <T> DataResult<ClaimStore> decode(Dynamic<T> input, ClaimStore store) {
        Dynamic<T> migrated = migrate(input);
        return ROOT.parse(migrated).map(root -> {
            for (StoredClaim sc : root.claims()) {
                store.load(sc.toClaim());
            }
            for (StoredLimits l : root.playerLimits()) {
                l.chunks().ifPresent(v -> store.setPlayerLimit(l.player(), PlayerLimits.Key.CHUNKS, v));
                l.claims().ifPresent(v -> store.setPlayerLimit(l.player(), PlayerLimits.Key.CLAIMS, v));
                l.members().ifPresent(v -> store.setPlayerLimit(l.player(), PlayerLimits.Key.MEMBERS, v));
            }
            return store;
        });
    }

    /** Upgrades older layouts to {@link #FORMAT_VERSION}. Missing version means version 1. */
    static <T> Dynamic<T> migrate(Dynamic<T> input) {
        int version = input.get("format_version").asInt(1);
        Dynamic<T> data = input;
        // 1 → 2: only optional fields were added (claim settings, player limits), nothing to rewrite.
        // Future: if (version < 3) { data = ...; version = 3; }
        if (version > FORMAT_VERSION) {
            throw new IllegalStateException("Claims data format " + version + " is newer than supported "
                    + FORMAT_VERSION + ". Refusing to load to avoid data loss.");
        }
        return data.set("format_version", data.createInt(FORMAT_VERSION));
    }
}
