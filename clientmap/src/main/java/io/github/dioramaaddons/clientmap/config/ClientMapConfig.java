package io.github.dioramaaddons.clientmap.config;

import io.github.dioramaaddons.clientmap.core.ShareMode;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Common config, {@code config/dioaddons_clientmap-common.toml}. The server's copy is the one that counts. */
public final class ClientMapConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.EnumValue<ShareMode> EXPLORED_MODE;
    public static final ModConfigSpec.IntValue EXPLORED_CHUNKS_PER_SECOND;
    public static final ModConfigSpec.IntValue EXPLORED_BATCH;

    public static final ModConfigSpec.EnumValue<ShareMode> LOCATIONS_MODE;
    public static final ModConfigSpec.IntValue PLAYER_UPDATE_TICKS;
    public static final ModConfigSpec.BooleanValue DEFAULT_VISIBLE;
    public static final ModConfigSpec.IntValue MAX_WAYPOINTS;

    public static final ModConfigSpec.BooleanValue SHOW_CLAIMS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("exploredMap");
        EXPLORED_MODE = b.comment("Shared explored map: DISABLED, GLOBAL (everyone shares one map) or GROUPS",
                "(each group of friends shares one; players without a group keep their own).")
                .defineEnum("mode", ShareMode.GLOBAL);
        EXPLORED_CHUNKS_PER_SECOND = b.comment("Explored chunks sent to each player per second while their map",
                "catches up. Each chunk is a few kilobytes.").defineInRange("chunksPerSecond", 16, 1, 512);
        EXPLORED_BATCH = b.comment("Chunks per network packet.").defineInRange("batchSize", 8, 1, 32);
        b.pop();

        b.push("locations");
        LOCATIONS_MODE = b.comment("Shared waypoints and player positions: DISABLED, GLOBAL or GROUPS.")
                .defineEnum("mode", ShareMode.GROUPS);
        PLAYER_UPDATE_TICKS = b.comment("Ticks between player position updates (20 = one second).")
                .defineInRange("playerUpdateTicks", 20, 5, 200);
        DEFAULT_VISIBLE = b.comment("Whether a player's position is shared until they choose otherwise",
                "(/maplocation show|hide).").define("defaultVisible", true);
        MAX_WAYPOINTS = b.comment("Maximum shared waypoints in one pool (global, a group, or a player alone).")
                .defineInRange("maxWaypointsPerPool", 200, 0, 10_000);
        b.pop();

        b.push("claims");
        SHOW_CLAIMS = b.comment("Send land claims (name, owner, chunks) to clients when Diorama Addons: Claims is",
                "installed. Members and permissions are never sent.").define("showClaims", true);
        b.pop();
        SPEC = b.build();
    }

    private ClientMapConfig() {}
}
