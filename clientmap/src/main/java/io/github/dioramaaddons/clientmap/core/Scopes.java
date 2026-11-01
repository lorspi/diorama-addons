package io.github.dioramaaddons.clientmap.core;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Names of the pools data is shared in: {@code global}, {@code group:<id>} or {@code player:<id>}. */
public final class Scopes {
    public static final String GLOBAL = "global";

    private Scopes() {}

    /** The pool a player reads and writes, or null when sharing is disabled. */
    public static @Nullable String of(ShareMode mode, UUID player, @Nullable UUID group) {
        return switch (mode) {
            case DISABLED -> null;
            case GLOBAL -> GLOBAL;
            case GROUPS -> group != null ? group(group) : player(player);
        };
    }

    public static String group(UUID group) {
        return "group:" + group;
    }

    public static String player(UUID player) {
        return "player:" + player;
    }
}
