package io.github.dioramaaddons.webmap.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config: {@code config/dioaddons_webmap-common.toml}. The {@code http}, {@code web} and {@code layers}
 * sections apply while the server runs (the file is watched, and {@code /webmap reload} reads it by hand); {@code render}
 * changes need a server restart.
 */
public final class WebMapConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue HTTP_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> BIND;
    public static final ModConfigSpec.IntValue PORT;
    public static final ModConfigSpec.IntValue HTTP_THREADS;

    public enum Style { DIORAMA, TOP_DOWN }

    public static final ModConfigSpec.BooleanValue RENDER_ENABLED;
    public static final ModConfigSpec.EnumValue<Style> STYLE;
    public static final ModConfigSpec.BooleanValue DOWNLOAD_CLIENT_ASSETS;
    public static final ModConfigSpec.IntValue MAX_CHUNKS_PER_SECOND;
    public static final ModConfigSpec.IntValue ZOOM_OUT_LEVELS;
    public static final ModConfigSpec.IntValue SCAN_INTERVAL_SECONDS;
    public static final ModConfigSpec.BooleanValue RENDER_MISSING_ON_START;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSIONS;

    public enum Language { AUTO, EN, ES }

    public static final ModConfigSpec.ConfigValue<String> TITLE;
    public static final ModConfigSpec.ConfigValue<String> FAVICON;
    public static final ModConfigSpec.EnumValue<Language> LANGUAGE;

    public static final ModConfigSpec.BooleanValue SHOW_CLAIMS;
    public static final ModConfigSpec.BooleanValue SHOW_PLAYERS;
    public static final ModConfigSpec.IntValue PLAYER_UPDATE_SECONDS;
    public static final ModConfigSpec.BooleanValue SHOW_ONLINE_LIST;
    public static final ModConfigSpec.BooleanValue SHOW_CHAT;
    public static final ModConfigSpec.BooleanValue CHAT_JOIN_LEAVE;
    public static final ModConfigSpec.IntValue CHAT_HISTORY;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Changes here restart the web server (not the game server) when the file is saved or after /webmap reload.",
                "/webmap restart restarts it by hand.").push("http");
        HTTP_ENABLED = b.comment("Start the embedded web server.").define("enabled", true);
        BIND = b.comment("Address to listen on. 0.0.0.0 = every interface, 127.0.0.1 = this machine only.")
                .define("bind", "0.0.0.0");
        PORT = b.comment("TCP port of the web map. If it is in use, an error is logged and the game server keeps",
                "running without the web map.").defineInRange("port", 8123, 1, 65535);
        HTTP_THREADS = b.comment("Threads answering web requests.").defineInRange("threads", 2, 1, 16);
        b.pop();

        b.comment("Look of the web page. Applied when the file is saved (or /webmap reload); open pages reload by themselves.").push("web");
        TITLE = b.comment("Title of the web page. Empty = the server's MOTD.").define("title", "");
        FAVICON = b.comment("Icon of the web page: an http(s) URL, or a file path relative to the server folder",
                "(png, ico, svg, gif, jpg or webp, up to 1 MB), e.g. \"server-icon.png\" or \"config/map-icon.png\".",
                "Empty = server-icon.png when the server has one, otherwise the mod's own icon.")
                .define("favicon", "");
        LANGUAGE = b.comment("Language of the web page. AUTO follows the visitor's browser.")
                .defineEnum("language", Language.AUTO);
        b.pop();

        b.comment("Changes in this section need a server restart.").push("render");
        RENDER_ENABLED = b.comment("Render tiles in the background.").define("enabled", true);
        STYLE = b.comment("DIORAMA: angled view with textures, sun and shadows, like the Diorama map.",
                "TOP_DOWN: flat view with vanilla map colours (faster). Changing it renders everything again.")
                .defineEnum("style", Style.DIORAMA);
        DOWNLOAD_CLIENT_ASSETS = b.comment("DIORAMA style: download the official Minecraft client jar from Mojang once",
                "(about 27 MB, checked against Mojang's SHA-1) to read block textures, which a server does not have.",
                "Modded blocks use the textures in their own jars. If off or offline, vanilla blocks use map colours.")
                .define("downloadClientAssets", true);
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

        b.comment("What the web page shows. Applied when the file is saved (or /webmap reload); open pages reload by themselves.")
                .push("layers");
        SHOW_CLAIMS = b.comment("Show land claims (name and owner only) when Diorama Addons: Claims is installed.",
                "Members and permissions are never published.").define("showClaims", true);
        SHOW_PLAYERS = b.comment("Show online players on the map (name and position). Off by default because the map",
                "may be public. Spectators are never shown.").define("showPlayers", false);
        PLAYER_UPDATE_SECONDS = b.comment("Seconds between player position and online list updates.")
                .defineInRange("playerUpdateSeconds", 2, 1, 60);
        SHOW_ONLINE_LIST = b.comment("Show the list of connected players (names only) on the web page.")
                .define("showOnlineList", true);
        SHOW_CHAT = b.comment("Show the public chat on the web page (read only). Off by default because the map may",
                "be public. Only messages sent while the server runs are kept, in memory.").define("showChat", false);
        CHAT_JOIN_LEAVE = b.comment("Also show players joining and leaving in the web chat.")
                .define("chatJoinLeave", true);
        CHAT_HISTORY = b.comment("Chat messages kept for the web page.").defineInRange("chatHistory", 100, 10, 1000);
        b.pop();
        SPEC = b.build();
    }

    private WebMapConfig() {}
}
