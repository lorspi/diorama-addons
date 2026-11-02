package io.github.dioramaaddons.webmap.render;

import com.mojang.logging.LogUtils;
import io.github.dioramaaddons.render.MapStyle;
import io.github.dioramaaddons.render.ObliqueRenderer;
import io.github.dioramaaddons.render.ObliqueTiles;
import io.github.dioramaaddons.webmap.assets.AssetSource;
import io.github.dioramaaddons.webmap.assets.BiomeTints;
import io.github.dioramaaddons.webmap.assets.ClientJar;
import io.github.dioramaaddons.webmap.assets.ServerBlockLooks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Renders the map at an angle, in the style of the Diorama map, on one minimum-priority background thread.
 * Next to each terrain tile it writes a hit map (see {@link ObliqueTiles}). Block colours come from textures:
 * the vanilla client jar is fetched once on this thread, so starting the server never waits for it.
 */
public final class ObliqueTileRenderer implements MapRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    private record TileKey(String dim, int level, int x, int y) {}

    private static final long PARENT_EVERY_NANOS = TimeUnit.SECONDS.toNanos(15);

    private final Map<String, ServerLevel> levels;
    private final TileStore tiles;
    private final TileStore hits;
    private final int zoomOutLevels;
    private final long nanosPerChunk;
    private final boolean downloadAssets;
    private final LiveChunkStore live;
    private final double sin;
    private final double cos;
    private final Set<TileKey> dirty = ConcurrentHashMap.newKeySet();
    private final Set<TileKey> dirtyParents = ConcurrentHashMap.newKeySet();
    private final Map<String, SavedVoxelWorld> worlds = new HashMap<>();
    /** Chunks changed since they were cached, by dimension; dropped from the cache before the next tile. */
    private final Map<String, Set<Long>> changed = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor;
    private final AtomicLong chunksRead = new AtomicLong();
    private final AtomicLong tilesWritten = new AtomicLong();
    private volatile @Nullable String current;
    private volatile boolean running = true;
    private @Nullable ServerBlockLooks looks;
    private @Nullable BiomeTints tints;
    private @Nullable AssetSource assets;
    private long nextSlot;

    public ObliqueTileRenderer(Map<String, ServerLevel> levels, Path root, int zoomOutLevels, int maxChunksPerSecond,
            int scanIntervalSeconds, boolean downloadAssets, LiveChunkStore live) {
        this.levels = levels;
        this.live = live;
        this.tiles = new TileStore(root.resolve("terrain"));
        this.hits = new TileStore(root.resolve("hits"));
        this.zoomOutLevels = zoomOutLevels;
        this.nanosPerChunk = 1_000_000_000L / Math.max(1, maxChunksPerSecond);
        this.downloadAssets = downloadAssets;
        this.sin = Math.sin(Math.toRadians(ObliqueRenderer.DEFAULT_PITCH));
        this.cos = Math.cos(Math.toRadians(ObliqueRenderer.DEFAULT_PITCH));
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dioaddons-webmap-render");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        executor.scheduleWithFixedDelay(this::process, scanIntervalSeconds, scanIntervalSeconds, TimeUnit.SECONDS);
    }

    public double sin() {
        return sin;
    }

    public double cos() {
        return cos;
    }

    public TileStore tiles() {
        return tiles;
    }

    public TileStore hits() {
        return hits;
    }

    @Override
    public void stop() {
        running = false;
        executor.shutdownNow();
        if (assets != null) {
            assets.close();
        }
    }

    private int minY(String dim) {
        ServerLevel l = levels.get(dim);
        return l == null ? -64 : l.getMinBuildHeight();
    }

    private int maxY(String dim) {
        ServerLevel l = levels.get(dim);
        return l == null ? 320 : l.getMaxBuildHeight() - 1;
    }

    // ---------------------------------------------------------------- marking

    /** Tiles that can show a chunk: its tile column (and the next one east, which its shadows reach). */
    @Override
    public void markChunk(String dim, int chunkX, int chunkZ) {
        if (!levels.containsKey(dim)) {
            return;
        }
        int z0 = chunkZ * 16;
        double vLow = z0 * sin - (maxY(dim) + 1) * cos;
        double vHigh = (z0 + 16) * sin - minY(dim) * cos;
        int tu = Math.floorDiv(chunkX * 16, ObliqueTiles.TILE_UNITS);
        boolean nearEast = (chunkX * 16 + 16) - (tu + 1) * ObliqueTiles.TILE_UNITS > -48;
        for (int tv = (int) Math.floor(vLow / ObliqueTiles.TILE_UNITS); tv <= (int) Math.floor(vHigh / ObliqueTiles.TILE_UNITS); tv++) {
            dirty.add(new TileKey(dim, 0, tu, tv));
            if (nearEast) {
                dirty.add(new TileKey(dim, 0, tu + 1, tv));
            }
        }
        changed.computeIfAbsent(dim, d -> ConcurrentHashMap.newKeySet()).add(net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ));
    }

    @Override
    public void scan(@Nullable String onlyDim, boolean force) {
        executor.execute(() -> {
            for (Map.Entry<String, ServerLevel> e : levels.entrySet()) {
                String dim = e.getKey();
                if (onlyDim != null && !onlyDim.equals(dim)) {
                    continue;
                }
                ServerLevel level = e.getValue();
                Path folder = DimensionType.getStorageFolder(level.dimension(),
                        level.getServer().getWorldPath(LevelResource.ROOT)).resolve("region");
                if (!Files.isDirectory(folder)) {
                    continue;
                }
                int before = dirty.size();
                try (Stream<Path> files = Files.list(folder)) {
                    for (Path f : files.toList()) {
                        Matcher m = REGION_FILE.matcher(f.getFileName().toString());
                        if (!m.matches()) {
                            continue;
                        }
                        int rx = Integer.parseInt(m.group(1));
                        int rz = Integer.parseInt(m.group(2));
                        long regionTime = Files.getLastModifiedTime(f).toMillis();
                        double vLow = rz * 512 * sin - (maxY(dim) + 1) * cos;
                        double vHigh = (rz * 512 + 512) * sin - minY(dim) * cos;
                        for (int tu = rx * 4; tu < rx * 4 + 4; tu++) {
                            for (int tv = (int) Math.floor(vLow / 128); tv <= (int) Math.floor(vHigh / 128); tv++) {
                                Path tile = tiles.path(TileStore.dimKey(dim), 0, tu, tv);
                                Path hit = hits.path(TileStore.dimKey(dim), 0, tu, tv);
                                Path done = Files.exists(hit) ? hit : emptyMarker(hit);
                                if (force || !Files.exists(done) || Files.getLastModifiedTime(done).toMillis() < regionTime
                                        || (Files.exists(tile) && !Files.exists(hit))) {
                                    dirty.add(new TileKey(dim, 0, tu, tv));
                                }
                            }
                        }
                    }
                } catch (IOException ex) {
                    LOGGER.warn("Could not list {}", folder, ex);
                }
                int queued = dirty.size() - before;
                if (queued > 0) {
                    LOGGER.info("Web map: {} tiles of {} queued for rendering", queued, dim);
                }
            }
        });
    }

    @Override
    public void clearCache() {
        executor.execute(() -> {
            dirty.clear();
            dirtyParents.clear();
            try {
                tiles.clear();
                hits.clear();
            } catch (IOException e) {
                LOGGER.error("Could not clear the web map cache", e);
            }
        });
        scan(null, true);
    }

    @Override
    public int queuedRegions() {
        return dirty.size();
    }

    @Override
    public int queuedParents() {
        return dirtyParents.size();
    }

    @Override
    public long chunksRendered() {
        return chunksRead.get();
    }

    @Override
    public long tilesWritten() {
        return tilesWritten.get();
    }

    @Override
    public @Nullable String current() {
        return current;
    }

    // ---------------------------------------------------------------- rendering

    private void ensureLooks() {
        if (looks != null) {
            return;
        }
        String version = SharedConstants.getCurrentVersion().getId();
        Path dir = FMLPaths.GAMEDIR.get().resolve("dioaddons_webmap");
        current = "fetching block textures";
        assets = new AssetSource(ClientJar.ensure(dir, version, downloadAssets));
        looks = new ServerBlockLooks(assets);
        ServerLevel any = levels.values().iterator().next();
        tints = new BiomeTints(any.getServer(), assets);
        if (!assets.hasVanilla()) {
            LOGGER.info("Web map: vanilla textures not available; vanilla blocks use map colours");
        }
    }

    private SavedVoxelWorld world(String dim) {
        return worlds.computeIfAbsent(dim, d -> new SavedVoxelWorld(levels.get(d), looks, tints, live, this::throttle));
    }

    private void process() {
        try {
            if (dirty.isEmpty() && dirtyParents.isEmpty()) {
                return;
            }
            ensureLooks();
            long nextParents = System.nanoTime() + PARENT_EVERY_NANOS;
            while (running && !dirty.isEmpty()) {
                TileKey key = dirty.iterator().next();
                dirty.remove(key);
                renderTile(key);
                // While players explore, level 0 is never done: refresh the zoomed-out levels now and then
                // instead of only at the end, or they stay empty (black) for as long as someone is moving.
                if (System.nanoTime() > nextParents) {
                    renderParents();
                    nextParents = System.nanoTime() + PARENT_EVERY_NANOS;
                }
            }
            renderParents();
        } catch (Throwable t) {
            if (running) {
                LOGGER.error("Web map render failed", t);
            }
        } finally {
            current = null;
        }
    }

    /** Zoomed-out levels, bottom up, so each one is built from fresh children. */
    private void renderParents() throws IOException {
        for (int level = 1; level <= zoomOutLevels && running; level++) {
            final int lv = level;
            Set<TileKey> batch = new HashSet<>();
            dirtyParents.removeIf(t -> t.level() == lv && batch.add(t));
            for (TileKey t : batch) {
                renderParent(t);
            }
        }
    }

    private void renderTile(TileKey key) throws IOException {
        if (!levels.containsKey(key.dim())) {
            return;
        }
        current = key.dim() + " tile " + key.x() + "," + key.y();
        SavedVoxelWorld world = world(key.dim());
        Set<Long> stale = changed.get(key.dim());
        if (stale != null) {
            for (var it = stale.iterator(); it.hasNext(); ) {
                long c = it.next();
                it.remove();
                world.invalidate(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c));
            }
        }
        double units = ObliqueTiles.units(0);
        double u0 = key.x() * units;
        double v0 = key.y() * units;
        // Highest ground that can be in view: rays start just above it.
        int minY = world.minY();
        int zA = (int) Math.floor((v0 + minY * cos) / sin);
        int zB = (int) Math.ceil((v0 + units + world.maxY() * cos) / sin);
        int top = Integer.MIN_VALUE;
        for (int cx = Math.floorDiv((int) u0, 16); cx <= Math.floorDiv((int) (u0 + units) - 1, 16); cx++) {
            for (int cz = Math.floorDiv(zA, 16); cz <= Math.floorDiv(zB, 16) && running; cz++) {
                top = Math.max(top, world.chunkTop(cx, cz));
            }
        }
        String dimKey = TileStore.dimKey(key.dim());
        if (top == Integer.MIN_VALUE) {
            tiles.write(dimKey, 0, key.x(), key.y(), new int[ObliqueTiles.PIXELS * ObliqueTiles.PIXELS]);
            hits.write(dimKey, 0, key.x(), key.y(), new int[ObliqueTiles.PIXELS * ObliqueTiles.PIXELS]);
            markEmpty(hits.path(dimKey, 0, key.x(), key.y()));
            return;
        }
        ObliqueRenderer renderer = new ObliqueRenderer(world, world.table(), MapStyle.DIORAMA, ObliqueRenderer.DEFAULT_PITCH);
        ObliqueRenderer.Tile tile = renderer.render(u0, v0, units, ObliqueTiles.PIXELS, top);
        if (!running) {
            return;
        }
        int zRef = ObliqueTiles.zRef(key.y(), 0, minY, sin, cos);
        tiles.write(dimKey, 0, key.x(), key.y(), tile.argb());
        Path hitPath = hits.path(dimKey, 0, key.x(), key.y());
        hits.write(dimKey, 0, key.x(), key.y(), ObliqueTiles.encode(tile, zRef, minY));
        if (Files.exists(hitPath)) {
            Files.deleteIfExists(emptyMarker(hitPath));
        } else {
            markEmpty(hitPath);
        }
        tilesWritten.incrementAndGet();
        if (zoomOutLevels > 0) {
            dirtyParents.add(new TileKey(key.dim(), 1, Math.floorDiv(key.x(), 2), Math.floorDiv(key.y(), 2)));
        }
    }

    private void renderParent(TileKey t) throws IOException {
        String dimKey = TileStore.dimKey(t.dim());
        current = t.dim() + " zoom " + t.level() + " " + t.x() + "," + t.y();
        int minY = minY(t.dim());
        int[][] terrain = new int[4][];
        int[][] hitMaps = new int[4][];
        int[] zRefs = new int[4];
        for (int q = 0; q < 4; q++) {
            int cx = 2 * t.x() + (q & 1);
            int cy = 2 * t.y() + (q >> 1);
            terrain[q] = tiles.read(dimKey, t.level() - 1, cx, cy);
            hitMaps[q] = hits.read(dimKey, t.level() - 1, cx, cy);
            zRefs[q] = ObliqueTiles.zRef(cy, t.level() - 1, minY, sin, cos);
        }
        tiles.write(dimKey, t.level(), t.x(), t.y(), Downsampler.combine(terrain, ObliqueTiles.PIXELS));
        hits.write(dimKey, t.level(), t.x(), t.y(), ObliqueTiles.downsampleHits(hitMaps, zRefs,
                ObliqueTiles.zRef(t.y(), t.level(), minY, sin, cos), ObliqueTiles.PIXELS));
        tilesWritten.incrementAndGet();
        if (t.level() < zoomOutLevels) {
            dirtyParents.add(new TileKey(t.dim(), t.level() + 1, Math.floorDiv(t.x(), 2), Math.floorDiv(t.y(), 2)));
        }
    }

    /** Written for tiles with nothing in view, so a restart does not render them again. */
    private static Path emptyMarker(Path hit) {
        return hit.resolveSibling(hit.getFileName().toString().replace(".png", ".empty"));
    }

    private static void markEmpty(Path hit) throws IOException {
        Files.createDirectories(hit.getParent());
        Files.writeString(emptyMarker(hit), "");
    }

    /** Keeps chunk reads at or below the configured chunks per second. */
    private void throttle() {
        chunksRead.incrementAndGet();
        long now = System.nanoTime();
        if (nextSlot > now) {
            long wait = nextSlot - now;
            try {
                Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
            now = nextSlot;
        }
        nextSlot = Math.max(nextSlot, now) + nanosPerChunk;
    }
}
