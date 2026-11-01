package io.github.dioramaaddons.webmap.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Common config: {@code config/dioaddons_webmap-common.toml}. Read when the server starts. */
public final class WebMapConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue HTTP_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> BIND;
    public static final ModConfigSpec.IntValue PORT;
    public static final ModConfigSpec.IntValue HTTP_THREADS;

    public static final ModConfigSpec.BooleanValue RENDER_ENABLED;
    public static final ModConfigSpec.IntValue MAX_CHUNKS_PER_SECOND;
    public static final ModConfigSpec.IntValue ZOOM_OUT_LEVELS;
    public static final ModConfigSpec.IntValue SCAN_INTERVAL_SECONDS;
    public static final ModConfigSpec.BooleanValue RENDER_MISSING_ON_START;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSIONS;

    public static final ModConfigSpec.BooleanValue SHOW_CLAIMS;
    public static final ModConfigSpec.BooleanValue SHOW_PLAYERS;
    public static final ModConfigSpec.IntValue PLAYER_UPDATE_SECONDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("http");
        HTTP_ENABLED = b.comment("Start the embedded web server.").define("enabled", true);
        BIND = b.comment("Address to listen on. 0.0.0.0 = every interface, 127.0.0.1 = this machine only.")
                .define("bind", "0.0.0.0");
        PORT = b.comment("TCP port of the web map. If it is in use, an error is logged and the game server keeps",
                "running without the web map.").defineInRange("port", 8123, 1, 65535);
        HTTP_THREADS = b.comment("Threads answering web requests.").defineInRange("threads", 2, 1, 16);
        b.pop();

        b.push("render");
        RENDER_ENABLED = b.comment("Render tiles in the background.").define("enabled", true);
        MAX_CHUNKS_PER_SECOND = b.comment("Upper bound of chunks read and rendered per second. Lower it if the",
                "server feels slow while tiles are generated.").defineInRange("maxChunksPerSecond", 200, 1, 10_000);
        ZOOM_OUT_LEVELS = b.comment("Number of zoomed-out levels generated (each halves the scale).")
                .defineInRange("zoomOutLevels", 4, 0, 8);
        SCAN_INTERVAL_SECONDS = b.comment("How often changed chunks are re-rendered.")
                .defineInRange("scanIntervalSeconds", 10, 1, 3600);
        RENDER_MISSING_ON_START = b.comment("On start, render regions that have no tile yet or whose region file",
                "is newer than its tile.").define("renderMissingOnStart", true);
        DIMENSIONS = b.comment("Dimensions shown on the map, e.g. [\"minecraft:overworld\"]. Empty = all.")
                .defineListAllowEmpty("dimensions", List.of(), () -> "minecraft:overworld", o -> o instanceof String);
        b.pop();

        b.push("layers");
        SHOW_CLAIMS = b.comment("Show land claims (name and owner only) when Diorama Addons: Claims is installed.",
                "Members and permissions are never published.").define("showClaims", true);
        SHOW_PLAYERS = b.comment("Publish online player positions. Off by default because the map may be public.")
                .define("showPlayers", false);
        PLAYER_UPDATE_SECONDS = b.comment("Seconds between player position updates.")
                .defineInRange("playerUpdateSeconds", 2, 1, 60);
        b.pop();
        SPEC = b.build();
    }

    private WebMapConfig() {}
}
