package io.github.dioramaaddons.clientmap.core;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Explored chunks of one pool in one dimension, in the order they were first explored. The position in
 * that order is the sequence number clients use to resume a sync.
 */
public final class ExploredLog {
    private final LongArrayList order = new LongArrayList();
    private final LongOpenHashSet set = new LongOpenHashSet();

    /** Adds a packed chunk position. Returns false if it was already explored. */
    public boolean add(long chunk) {
        if (set.add(chunk)) {
            order.add(chunk);
            return true;
        }
        return false;
    }

    public boolean contains(long chunk) {
        return set.contains(chunk);
    }

    public int size() {
        return order.size();
    }

    public long get(int seq) {
        return order.getLong(seq);
    }

    public long[] toArray() {
        return order.toLongArray();
    }

    public static ExploredLog of(long[] chunks) {
        ExploredLog log = new ExploredLog();
        for (long c : chunks) {
            log.add(c);
        }
        return log;
    }

    /** Appends every chunk of {@code other} not explored here yet. Returns how many were new. */
    public int merge(ExploredLog other) {
        int added = 0;
        for (int i = 0; i < other.size(); i++) {
            if (add(other.get(i))) {
                added++;
            }
        }
        return added;
    }
}
