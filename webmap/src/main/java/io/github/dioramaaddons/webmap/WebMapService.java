package io.github.dioramaaddons.webmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.dioramaaddons.webmap.config.WebMapConfig;
import io.github.dioramaaddons.webmap.http.WebServer;
import io.github.dioramaaddons.webmap.layer.ClaimsLayer;
import io.github.dioramaaddons.webmap.layer.PlayersLayer;
import io.github.dioramaaddons.render.ObliqueRenderer;
import io.github.dioramaaddons.render.ObliqueTiles;
import io.github.dioramaaddons.webmap.render.MapRenderer;
import io.github.dioramaaddons.webmap.render.ObliqueTileRenderer;
import io.github.dioramaaddons.webmap.render.TileMath;
import io.github.dioramaaddons.webmap.render.TileRenderer;
import io.github.dioramaaddons.webmap.render.TileStore;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final boolean claimsEnabled;
    private final PlayersLayer players;
    private final WebServer web;
    private final String infoJson;
    private final int zoomOutLevels;

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
        this.claimsEnabled = WebMapConfig.SHOW_CLAIMS.get();
        if (claimsEnabled) {
            claims.start();
        }
        this.players = new PlayersLayer(WebMapConfig.SHOW_PLAYERS.get(), WebMapConfig.PLAYER_UPDATE_SECONDS.get());
        this.infoJson = buildInfo();
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
        if (WebMapConfig.HTTP_ENABLED.get()) {
            s.web.start(WebMapConfig.BIND.get(), WebMapConfig.PORT.get(), WebMapConfig.HTTP_THREADS.get());
        }
    }

    public static void stop() {
        WebMapService s = instance;
        instance = null;
        if (s != null) {
            s.web.stop();
            if (s.renderer != null) {
                s.renderer.stop();
            }
            s.claims.stop();
        }
    }

    /** Server thread. */
    public void tick() {
        if (claimsEnabled) {
            claims.tick();
        }
        players.tick(server);
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
        return claimsEnabled && claims.available();
    }

    public Set<String> dimensions() {
        return levels.keySet();
    }

    private String buildInfo() {
        JsonObject o = new JsonObject();
        o.addProperty("name", server.getMotd());
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
        o.addProperty("claims", claimsAvailable());
        o.addProperty("players", players.enabled());
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
        return infoJson;
    }

    @Override
    public String claimsJson() {
        return claimsEnabled ? claims.json() : "[]";
    }

    @Override
    public String playersJson() {
        return players.json();
    }
}
