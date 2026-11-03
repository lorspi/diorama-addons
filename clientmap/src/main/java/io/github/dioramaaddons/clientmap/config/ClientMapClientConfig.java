package io.github.dioramaaddons.clientmap.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config, {@code config/dioaddons_clientmap-client.toml}: what this player wants to see on the Diorama map. */
public final class ClientMapClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue CLAIMS_ON_MAP;
    public static final ModConfigSpec.BooleanValue CLAIMS_ON_MINIMAP;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("claims");
        CLAIMS_ON_MAP = b.comment("Show land claims on the full Diorama map (Diorama's own claims setting still applies).")
                .define("claimsOnMap", true);
        CLAIMS_ON_MINIMAP = b.comment("Show land claims on the Diorama minimap (Diorama's own claims setting still applies).")
                .define("claimsOnMinimap", true);
        b.pop();
        SPEC = b.build();
    }

    private ClientMapClientConfig() {}

    /** The value, or {@code true} before the config is loaded. */
    public static boolean flag(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static void set(ModConfigSpec.BooleanValue value, boolean on) {
        value.set(on);
        SPEC.save();
    }
}
