package io.github.dioramaaddons.webmap.render;

import com.mojang.logging.LogUtils;
import io.github.dioramaaddons.claimsapi.ChunkCoord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Renders tiles on one background thread with minimum priority. Chunks are read through the server's own
 * chunk storage ({@code ChunkMap.read}), which is asynchronous, includes pending writes and never touches
 * the server thread. Changed chunks are collected in {@link #markChunk} and processed every scan interval.
 */
public final class TileRenderer implements MapRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    public record RegionKey(String dim, int x, int z) {}

    private record TileKey(String dim, int zoom, int x, int y) {}

    private final Map<String, ServerLevel> levels;
    private final TileStore store;
    private final BlockColorSource colors = new McBlockColors();
    private final int zoomOutLevels;
    private final long nanosPerChunk;
    private final Map<RegionKey, Set<Long>> dirty = new ConcurrentHashMap<>();
    private final Set<TileKey> dirtyParents = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService executor;
    private final AtomicLong chunksRendered = new AtomicLong();
    private final AtomicLong tilesWritten = new AtomicLong();
    private volatile @Nullable String current;
    private volatile boolean running = true;
    private long nextSlot;

    /** @param levels dimension id → level, only the dimensions shown on the map */
    public TileRenderer(Map<String, ServerLevel> levels, TileStore store, int zoomOutLevels, int maxChunksPerSecond,
            int scanIntervalSeconds) {
        this.levels = levels;
        this.store = store;
        this.zoomOutLevels = zoomOutLevels;
        this.nanosPerChunk = 1_000_000_000L / Math.max(1, maxChunksPerSecond);
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dioaddons-webmap-render");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        executor.scheduleWithFixedDelay(this::process, scanIntervalSeconds, scanIntervalSeconds, TimeUnit.SECONDS);
    }

    public void stop() {
        running = false;
        executor.shutdownNow();
    }

    // ---------------------------------------------------------------- marking (any thread)

    public void markChunk(String dim, int chunkX, int chunkZ) {
        if (!levels.containsKey(dim)) {
            return;
        }
        RegionKey key = new RegionKey(dim, TileMath.regionOfChunk(chunkX), TileMath.regionOfChunk(chunkZ));
        dirty.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(ChunkCoord.pack(chunkX, chunkZ));
    }

    public void markRegion(String dim, int regionX, int regionZ) {
        Set<Long> set = dirty.computeIfAbsent(new RegionKey(dim, regionX, regionZ), k -> ConcurrentHashMap.newKeySet());
        for (int z = 0; z < 32; z++) {
            for (int x = 0; x < 32; x++) {
                set.add(ChunkCoord.pack(regionX * 32 + x, regionZ * 32 + z));
            }
        }
    }

    /**
     * Queues regions found on disk. With {@code force} every region is re-rendered, otherwise only regions
     * without a tile or with a region file newer than the tile. Runs on the render thread.
     */
    public void scan(@Nullable String onlyDim, boolean force) {
        executor.execute(() -> {
            for (Map.Entry<String, ServerLevel> e : levels.entrySet()) {
                if (onlyDim != null && !onlyDim.equals(e.getKey())) {
                    continue;
                }
                ServerLevel level = e.getValue();
                Path folder = DimensionType.getStorageFolder(level.dimension(),
                        level.getServer().getWorldPath(LevelResource.ROOT)).resolve("region");
                if (!Files.isDirectory(folder)) {
                    continue;
                }
                int queued = 0;
                try (Stream<Path> files = Files.list(folder)) {
                    for (Path f : files.toList()) {
                        Matcher m = REGION_FILE.matcher(f.getFileName().toString());
                        if (!m.matches()) {
                            continue;
                        }
                        int rx = Integer.parseInt(m.group(1));
                        int rz = Integer.parseInt(m.group(2));
                        Path tile = store.path(TileStore.dimKey(e.getKey()), 0, rx, rz);
                        if (force || !Files.exists(tile)
                                || Files.getLastModifiedTime(f).compareTo(Files.getLastModifiedTime(tile)) > 0) {
                            markRegion(e.getKey(), rx, rz);
                            queued++;
                        }
                    }
                } catch (IOException ex) {
                    LOGGER.warn("Could not list {}", folder, ex);
                }
                if (queued > 0) {
                    LOGGER.info("Web map: {} regions of {} queued for rendering", queued, e.getKey());
                }
            }
        });
    }

    /** Deletes every tile and renders everything again. */
    public void clearCache() {
        executor.execute(() -> {
            dirty.clear();
            dirtyParents.clear();
            try {
                store.clear();
            } catch (IOException e) {
                LOGGER.error("Could not clear the web map cache", e);
            }
        });
        scan(null, true);
    }

    // ---------------------------------------------------------------- status

    public int queuedRegions() {
        return dirty.size();
    }

    public int queuedParents() {
        return dirtyParents.size();
    }

    public long chunksRendered() {
        return chunksRendered.get();
    }

    public long tilesWritten() {
        return tilesWritten.get();
    }

    public @Nullable String current() {
        return current;
    }

    // ---------------------------------------------------------------- rendering (render thread)

    private void process() {
        try {
            while (running && !dirty.isEmpty()) {
                RegionKey key = dirty.keySet().iterator().next();
                Set<Long> chunks = dirty.remove(key);
                if (chunks != null && !chunks.isEmpty()) {
                    renderRegion(key, chunks);
                }
            }
            for (int zoom = 1; zoom <= zoomOutLevels && running; zoom++) {
                final int z = zoom;
                Set<TileKey> level = new HashSet<>();
                dirtyParents.removeIf(t -> t.zoom() == z && level.add(t));
                for (TileKey t : level) {
                    renderParent(t);
                }
            }
        } catch (Throwable t) {
            if (running) {
                LOGGER.error("Web map render failed", t);
            }
        } finally {
            current = null;
        }
    }

    private void renderRegion(RegionKey key, Set<Long> chunks) throws IOException {
        ServerLevel level = levels.get(key.dim());
        if (level == null) {
            return;
        }
        String dimKey = TileStore.dimKey(key.dim());
        current = key.dim() + " r." + key.x() + "." + key.z();
        int[] tile = store.read(dimKey, 0, key.x(), key.z());
        if (tile == null) {
            tile = new int[TileMath.TILE_SIZE * TileMath.TILE_SIZE];
        }
        Map<Long, Optional<ChunkData>> cache = new HashMap<>();
        // Row by row from north to south so the north neighbour is usually cached already.
        for (int cz = key.z() * 32; cz < key.z() * 32 + 32 && running; cz++) {
            for (int cx = key.x() * 32; cx < key.x() * 32 + 32 && running; cx++) {
                if (!chunks.contains(ChunkCoord.pack(cx, cz))) {
                    continue;
                }
                ChunkData data = load(level, cx, cz, cache);
                int[] pixels = data == null ? new int[256]
                        : ChunkRenderer.render(data, load(level, cx, cz - 1, cache), colors);
                int ox = (cx - key.x() * 32) * 16;
                int oz = (cz - key.z() * 32) * 16;
                for (int z = 0; z < 16; z++) {
                    System.arraycopy(pixels, z * 16, tile, (oz + z) * TileMath.TILE_SIZE + ox, 16);
                }
                if (data != null) {
                    chunksRendered.incrementAndGet();
                }
            }
        }
        if (!running) {
            return;
        }
        store.write(dimKey, 0, key.x(), key.z(), tile);
        tilesWritten.incrementAndGet();
        if (zoomOutLevels > 0) {
            dirtyParents.add(new TileKey(key.dim(), 1, TileMath.parent(key.x()), TileMath.parent(key.z())));
        }
    }

    private void renderParent(TileKey t) throws IOException {
        String dimKey = TileStore.dimKey(t.dim());
        current = t.dim() + " zoom " + t.zoom() + " " + t.x() + "," + t.y();
        int[][] children = new int[4][];
        children[0] = store.read(dimKey, t.zoom() - 1, 2 * t.x(), 2 * t.y());
        children[1] = store.read(dimKey, t.zoom() - 1, 2 * t.x() + 1, 2 * t.y());
        children[2] = store.read(dimKey, t.zoom() - 1, 2 * t.x(), 2 * t.y() + 1);
        children[3] = store.read(dimKey, t.zoom() - 1, 2 * t.x() + 1, 2 * t.y() + 1);
        store.write(dimKey, t.zoom(), t.x(), t.y(), Downsampler.combine(children, TileMath.TILE_SIZE));
        tilesWritten.incrementAndGet();
        if (t.zoom() < zoomOutLevels) {
            dirtyParents.add(new TileKey(t.dim(), t.zoom() + 1, TileMath.parent(t.x()), TileMath.parent(t.y())));
        }
    }

    private @Nullable ChunkData load(ServerLevel level, int cx, int cz, Map<Long, Optional<ChunkData>> cache) {
        long key = ChunkCoord.pack(cx, cz);
        Optional<ChunkData> cached = cache.get(key);
        if (cached != null) {
            return cached.orElse(null);
        }
        ChunkData data = null;
        try {
            throttle();
            Optional<CompoundTag> tag = level.getChunkSource().chunkMap.read(new ChunkPos(cx, cz)).get(30, TimeUnit.SECONDS);
            if (tag.isPresent()) {
                data = ChunkNbt.parse(tag.get(), level.getMinBuildHeight(), level.getHeight());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        } catch (Exception e) {
            if (running) {
                LOGGER.debug("Could not read chunk {}, {} of {}", cx, cz, level.dimension().location(), e);
            }
        }
        cache.put(key, Optional.ofNullable(data));
        return data;
    }

    /** Keeps reads at or below the configured chunks per second. */
    private void throttle() throws InterruptedException {
        long now = System.nanoTime();
        if (nextSlot > now) {
            long wait = nextSlot - now;
            Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
            now = nextSlot;
        }
        nextSlot = Math.max(nextSlot, now) + nanosPerChunk;
    }
}
