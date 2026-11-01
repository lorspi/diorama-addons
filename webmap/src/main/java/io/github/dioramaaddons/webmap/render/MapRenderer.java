package io.github.dioramaaddons.webmap.render;

import org.jetbrains.annotations.Nullable;

/** A background tile renderer: top-down ({@link TileRenderer}) or angled ({@link ObliqueTileRenderer}). */
public interface MapRenderer {
    void markChunk(String dim, int chunkX, int chunkZ);

    void scan(@Nullable String onlyDim, boolean force);

    void clearCache();

    void stop();

    int queuedRegions();

    int queuedParents();

    long chunksRendered();

    long tilesWritten();

    @Nullable String current();
}
