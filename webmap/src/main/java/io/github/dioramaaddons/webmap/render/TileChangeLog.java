package io.github.dioramaaddons.webmap.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Recent tile writes, so the web page can reload only the tiles that changed instead of polling every tile.
 * A bounded ring: a client that fell too far behind (or a cleared cache) is told to reload everything.
 * Thread-safe; pure Java.
 */
public final class TileChangeLog {
    /** One changed tile. */
    public record Change(String dimKey, int zoom, int x, int y) {}

    /** Changes after a sequence number. {@code reset} means the client must reload every tile. */
    public record Since(long seq, boolean reset, List<Change> changes) {}

    private final Change[] ring;
    private long seq;
    /** Sequence numbers at or below this one cannot be answered (ring overwritten or cache cleared). */
    private long floor;

    public TileChangeLog(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity");
        }
        this.ring = new Change[capacity];
    }

    public synchronized void record(String dimKey, int zoom, int x, int y) {
        ring[(int) (seq % ring.length)] = new Change(dimKey, zoom, x, y);
        seq++;
        floor = Math.max(floor, seq - ring.length);
    }

    /** Forces every client to reload all tiles (e.g. after the cache was cleared). */
    public synchronized void reset() {
        seq++;
        floor = seq;
    }

    public synchronized long seq() {
        return seq;
    }

    /**
     * Changes recorded after {@code since}. A negative {@code since} (first call) or one that is newer than
     * the log (server restarted) only returns the current sequence number.
     */
    public synchronized Since since(long since) {
        if (since < 0 || since > seq) {
            return new Since(seq, false, List.of());
        }
        if (since < floor) {
            return new Since(seq, true, List.of());
        }
        List<Change> out = new ArrayList<>((int) (seq - since));
        for (long s = since; s < seq; s++) {
            out.add(ring[(int) (s % ring.length)]);
        }
        return new Since(seq, false, out);
    }
}
