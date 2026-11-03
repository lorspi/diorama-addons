package io.github.dioramaaddons.claims.core;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * One player's own limits, set by an operator. A missing value means the server limit applies. Being a member of
 * other players' claims never counts towards these limits.
 */
public final class PlayerLimits {
    public enum Key {
        CHUNKS, CLAIMS, MEMBERS;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Key> byId(String id) {
            for (Key k : values()) {
                if (k.id().equalsIgnoreCase(id)) {
                    return Optional.of(k);
                }
            }
            return Optional.empty();
        }
    }

    private final Map<Key, Integer> values = new EnumMap<>(Key.class);

    public Optional<Integer> get(Key key) {
        return Optional.ofNullable(values.get(key));
    }

    /** Sets the player's own value, or clears it with {@code null}. */
    public void set(Key key, Integer value) {
        if (value == null) {
            values.remove(key);
        } else {
            values.put(key, Math.max(0, value));
        }
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Map<Key, Integer> asMap() {
        return Map.copyOf(values);
    }
}
