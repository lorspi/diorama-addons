package io.github.dioramaaddons.claimsapi;

import java.util.Optional;

/** Static access point. The claims mod registers its implementation when a server starts. */
public final class ClaimsApiProvider {
    private static volatile ClaimsApi instance;

    private ClaimsApiProvider() {}

    public static Optional<ClaimsApi> get() {
        return Optional.ofNullable(instance);
    }

    public static void register(ClaimsApi api) {
        instance = api;
    }

    public static void unregister(ClaimsApi api) {
        if (instance == api) {
            instance = null;
        }
    }
}
