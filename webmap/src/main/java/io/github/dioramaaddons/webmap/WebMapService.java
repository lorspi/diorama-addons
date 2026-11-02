package io.github.dioramaaddons.webmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.dioramaaddons.webmap.config.WebMapConfig;
import io.github.dioramaaddons.webmap.http.WebServer;
import io.github.dioramaaddons.webmap.layer.ChatLayer;
import io.github.dioramaaddons.webmap.layer.ClaimsLayer;
import io.github.dioramaaddons.webmap.layer.PlayersLayer;
import io.github.dioramaaddons.render.ObliqueRenderer;
import io.github.dioramaaddons.render.ObliqueTiles;
import io.github.dioramaaddons.webmap.render.MapRenderer;
import io.github.dioramaaddons.webmap.render.ObliqueTileRenderer;
import io.github.dioramaaddons.webmap.render.TileMath;
import io.github.dioramaaddons.webmap.render.TileRenderer;
import io.github.dioramaaddons.webmap.render.TileChangeLog;
import io.github.dioramaaddons.webmap.render.TileStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/** Web map state for one running server. */
public final class WebMapService implements WebServer.Source {
    private static volatile @Nullable WebMapService instance;

    private final MinecraftServer server;
    private final Map<String, ServerLevel> levels = new LinkedHashMap<>();
    private final Set<String> dimKeys;
    private final TileStore store;
    private final @Nullable TileStore hitStore;
    private final boolean oblique;
    private final @Nullable MapRenderer renderer;
    private final ClaimsLayer claims = new ClaimsLayer();
    private final PlayersLayer players = new PlayersLayer();
    private final ChatLayer chat = new ChatLayer();
    private final TileChangeLog changes = new TileChangeLog(8192);
    private final WebServer web;
    private final int zoomOutLevels;
    /** Settings that may change while running; rebuilt by {@link #applyConfig()}. */
    private volatile Live live;
    /** HTTP settings the web server was last started with. */
    private @Nullable String httpSettings;

    private record Live(String infoJson, String title, WebServer.@Nullable Favicon favicon, boolean claims,
            boolean positions, boolean online, boolean chat, boolean joinLeave, int chatHistory, int playerSeconds) {}

    private WebMapService(MinecraftServer server) {
        this.server = server;
        List<? extends String> allowed = WebMapConfig.DIMENSIONS.get();
        for (ServerLevel level : server.getAllLevels()) {
            String id = level.dimension().location().toString();
            if (allowed.isEmpty() || allowed.contains(id)) {
                levels.put(id, level);
            }
        }
        this.dimKeys = levels.keySet().stream().map(TileStore::dimKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
        Path root = server.getWorldPath(LevelResource.ROOT).resolve(WebMapMod.MOD_ID);
        this.zoomOutLevels = WebMapConfig.ZOOM_OUT_LEVELS.get();
        this.oblique = WebMapConfig.STYLE.get() == WebMapConfig.Style.DIORAMA;
        if (oblique) {
            ObliqueTileRenderer r = new ObliqueTileRenderer(Map.copyOf(levels), root.resolve("diorama"), zoomOutLevels,
                    WebMapConfig.MAX_CHUNKS_PER_SECOND.get(), WebMapConfig.SCAN_INTERVAL_SECONDS.get(),
                    WebMapConfig.DOWNLOAD_CLIENT_ASSETS.get());
            this.store = r.tiles();
            this.hitStore = r.hits();
            this.renderer = WebMapConfig.RENDER_ENABLED.get() ? r : null;
            if (renderer == null) {
                r.stop();
            }
        } else {
            this.store = new TileStore(root.resolve("tiles"));
            this.hitStore = null;
            this.renderer = WebMapConfig.RENDER_ENABLED.get()
                    ? new TileRenderer(Map.copyOf(levels), store, zoomOutLevels, WebMapConfig.MAX_CHUNKS_PER_SECOND.get(),
                            WebMapConfig.SCAN_INTERVAL_SECONDS.get())
                    : null;
        }
        store.reportTo(changes);
        if (hitStore != null) {
            hitStore.reportTo(changes);
        }
        claims.start();
        this.live = buildLive();
        this.web = new WebServer(this);
    }

    public static @Nullable WebMapService get() {
        return instance;
    }

    public static void start(MinecraftServer server) {
        WebMapService s = new WebMapService(server);
        instance = s;
        if (s.renderer != null && WebMapConfig.RENDER_MISSING_ON_START.get()) {
            s.renderer.scan(null, false);
        }
        s.startHttp(false);
    }

    /** Starts, or restarts, the web server with the current config. Returns whether it is running after. */
    public synchronized boolean startHttp(boolean force) {
        String settings = WebMapConfig.HTTP_ENABLED.get() + "|" + WebMapConfig.BIND.get() + "|" + WebMapConfig.PORT.get()
                + "|" + WebMapConfig.HTTP_THREADS.get();
        if (!force && settings.equals(httpSettings) && (web.running() || !WebMapConfig.HTTP_ENABLED.get())) {
            return web.running();
        }
        httpSettings = settings;
        web.stop();
        if (WebMapConfig.HTTP_ENABLED.get()) {
            web.start(WebMapConfig.BIND.get(), WebMapConfig.PORT.get(), WebMapConfig.HTTP_THREADS.get());
        } else {
            WebMapMod.LOGGER.info("Web map HTTP server disabled in the config");
        }
        return web.running();
    }

    /** The config file changed (any thread): apply page and layer settings, restart HTTP if its settings changed. */
    public void applyConfig() {
        live = buildLive();
        if (!live.chat()) {
            chat.clear();
        }
        startHttp(false);
    }

    public static void stop() {
        WebMapService s = instance;
        instance = null;
        if (s != null) {
            synchronized (s) {
                s.web.stop();
            }
            if (s.renderer != null) {
                s.renderer.stop();
            }
            s.claims.stop();
        }
    }

    /** Server thread. */
    public void tick() {
        Live l = live;
        if (l.claims()) {
            claims.tick();
        }
        players.tick(server, l.positions(), l.online(), l.playerSeconds());
    }

    /** Server thread: a public chat message. */
    public void onChat(String name, String text) {
        Live l = live;
        if (l.chat()) {
            chat.add(ChatLayer.Kind.CHAT, name, text, l.chatHistory());
        }
    }

    /** Server thread: a player joined or left. */
    public void onJoinLeave(String name, boolean joined) {
        Live l = live;
        if (l.chat() && l.joinLeave()) {
            chat.add(joined ? ChatLayer.Kind.JOIN : ChatLayer.Kind.LEAVE, name, "", l.chatHistory());
        }
    }

    public void markChunk(ServerLevel level, int chunkX, int chunkZ) {
        if (renderer != null) {
            renderer.markChunk(level.dimension().location().toString(), chunkX, chunkZ);
        }
    }

    public @Nullable MapRenderer renderer() {
        return renderer;
    }

    public WebServer web() {
        return web;
    }

    public boolean claimsAvailable() {
        return live.claims() && claims.available();
    }

    public boolean playersShown() {
        return live.positions();
    }

    public boolean chatShown() {
        return live.chat();
    }

    public boolean onlineListShown() {
        return live.online();
    }

    private Live buildLive() {
        String title = WebMapConfig.TITLE.get().strip();
        if (title.isEmpty()) {
            title = ChatFormatting.stripFormatting(server.getMotd());
        }
        if (title == null || title.isBlank()) {
            title = "World Map";
        }
        boolean claimsOn = WebMapConfig.SHOW_CLAIMS.get();
        boolean positions = WebMapConfig.SHOW_PLAYERS.get();
        boolean online = WebMapConfig.SHOW_ONLINE_LIST.get();
        boolean chatOn = WebMapConfig.SHOW_CHAT.get();
        WebServer.Favicon favicon = resolveFavicon(WebMapConfig.FAVICON.get().strip());
        String info = buildInfo(title, claimsOn && claims.available(), positions, online, chatOn);
        return new Live(info, title, favicon, claimsOn, positions, online, chatOn, WebMapConfig.CHAT_JOIN_LEAVE.get(),
                WebMapConfig.CHAT_HISTORY.get(), WebMapConfig.PLAYER_UPDATE_SECONDS.get());
    }

    private static final long MAX_FAVICON_BYTES = 1024 * 1024;

    private WebServer.@Nullable Favicon resolveFavicon(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return new WebServer.Favicon(value, null, "");
        }
        Path dir = server.getServerDirectory();
        Path file;
        if (value.isEmpty()) {
            file = dir.resolve("server-icon.png");
            if (!Files.isRegularFile(file)) {
                return null;
            }
        } else {
            file = dir.resolve(value).normalize();
        }
        String type = faviconType(file.getFileName() == null ? "" : file.getFileName().toString());
        if (type == null) {
            WebMapMod.LOGGER.warn("web.favicon must be an http(s) URL or a png, ico, svg, gif, jpg or webp file: {}", value);
            return null;
        }
        try {
            if (Files.size(file) > MAX_FAVICON_BYTES) {
                WebMapMod.LOGGER.warn("web.favicon is larger than 1 MB: {}", file);
                return null;
            }
            return new WebServer.Favicon(null, Files.readAllBytes(file), type);
        } catch (IOException e) {
            WebMapMod.LOGGER.warn("Could not read web.favicon {}: {}", file, e.toString());
            return null;
        }
    }

    /** Content type of an icon file, or null when the extension is not an image (never serve other files). */
    static @Nullable String faviconType(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".ico")) return "image/x-icon";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".webp")) return "image/webp";
        return null;
    }

    public Set<String> dimensions() {
        return levels.keySet();
    }

    private String buildInfo(String title, boolean claimsOn, boolean positions, boolean online, boolean chatOn) {
        JsonObject o = new JsonObject();
        o.addProperty("revision", System.nanoTime());
        o.addProperty("name", title);
        o.addProperty("language", WebMapConfig.LANGUAGE.get().name().toLowerCase(Locale.ROOT));
        o.addProperty("minecraftVersion", SharedConstants.getCurrentVersion().getName());
        o.addProperty("tileSize", oblique ? ObliqueTiles.PIXELS : TileMath.TILE_SIZE);
        JsonObject projection = new JsonObject();
        if (oblique) {
            double pitch = Math.toRadians(ObliqueRenderer.DEFAULT_PITCH);
            projection.addProperty("type", "oblique");
            projection.addProperty("sin", Math.sin(pitch));
            projection.addProperty("cos", Math.cos(pitch));
            projection.addProperty("tileUnits", ObliqueTiles.TILE_UNITS);
            projection.addProperty("pixelsPerUnit", ObliqueTiles.PIXELS / ObliqueTiles.TILE_UNITS);
        } else {
            projection.addProperty("type", "topdown");
        }
        o.add("projection", projection);
        o.addProperty("zoomOutLevels", zoomOutLevels);
        o.addProperty("claims", claimsOn);
        o.addProperty("players", positions);
        o.addProperty("online", online);
        o.addProperty("chat", chatOn);
        JsonArray dims = new JsonArray();
        for (String id : levels.keySet()) {
            JsonObject d = new JsonObject();
            d.addProperty("id", id);
            d.addProperty("key", TileStore.dimKey(id));
            d.addProperty("minY", levels.get(id).getMinBuildHeight());
            dims.add(d);
        }
        o.add("dimensions", dims);
        BlockPos spawn = server.overworld().getSharedSpawnPos();
        JsonObject s = new JsonObject();
        s.addProperty("x", spawn.getX());
        s.addProperty("z", spawn.getZ());
        o.add("spawn", s);
        return o.toString();
    }

    // ---------------------------------------------------------------- WebServer.Source (HTTP threads)

    @Override
    public Set<String> dimensionKeys() {
        return dimKeys;
    }

    @Override
    public Path tilePath(String dimKey, int zoom, int x, int y) {
        return store.path(dimKey, zoom, x, y);
    }

    @Override
    public @Nullable Path hitPath(String dimKey, int zoom, int x, int y) {
        return hitStore == null ? null : hitStore.path(dimKey, zoom, x, y);
    }

    @Override
    public String infoJson() {
        return live.infoJson();
    }

    @Override
    public String claimsJson() {
        return live.claims() ? claims.json() : "[]";
    }

    @Override
    public String chatJson(long since) {
        return chat.json(live.chat(), Math.max(0, since));
    }

    @Override
    public String changesJson(long since) {
        TileChangeLog.Since r = changes.since(since);
        JsonObject o = new JsonObject();
        o.addProperty("seq", r.seq());
        o.addProperty("reset", r.reset());
        JsonArray list = new JsonArray();
        java.util.Set<TileChangeLog.Change> seen = new java.util.HashSet<>();
        for (TileChangeLog.Change c : r.changes()) {
            if (seen.add(c)) {
                JsonArray a = new JsonArray();
                a.add(c.dimKey());
                a.add(c.zoom());
                a.add(c.x());
                a.add(c.y());
                list.add(a);
            }
        }
        o.add("tiles", list);
        return o.toString();
    }

    @Override
    public String title() {
        return live.title();
    }

    @Override
    public WebServer.@Nullable Favicon favicon() {
        return live.favicon();
    }

    @Override
    public String playersJson() {
        return players.json();
    }
}
