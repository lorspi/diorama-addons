package io.github.dioramaaddons.webmap.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TileChangeLogTest {
    @Test
    void firstCallOnlyReturnsTheSequence() {
        TileChangeLog log = new TileChangeLog(4);
        log.record("d", 0, 1, 2);
        TileChangeLog.Since r = log.since(-1);
        assertEquals(1, r.seq());
        assertFalse(r.reset());
        assertTrue(r.changes().isEmpty());
    }

    @Test
    void returnsChangesAfterTheGivenSequence() {
        TileChangeLog log = new TileChangeLog(4);
        log.record("d", 0, 1, 2);
        long seq = log.seq();
        log.record("d", 1, 3, 4);
        log.record("d", 0, 5, 6);
        TileChangeLog.Since r = log.since(seq);
        assertEquals(3, r.seq());
        assertEquals(List.of(new TileChangeLog.Change("d", 1, 3, 4), new TileChangeLog.Change("d", 0, 5, 6)), r.changes());
        assertTrue(log.since(r.seq()).changes().isEmpty());
    }

    @Test
    void tooOldOrClearedMeansReset() {
        TileChangeLog log = new TileChangeLog(2);
        for (int i = 0; i < 5; i++) {
            log.record("d", 0, i, 0);
        }
        assertTrue(log.since(1).reset());
        assertFalse(log.since(3).reset());
        long seq = log.seq();
        log.reset();
        assertTrue(log.since(seq).reset());
        assertFalse(log.since(log.seq()).reset());
    }

    @Test
    void sequenceFromAnotherServerRunIsIgnored() {
        TileChangeLog log = new TileChangeLog(4);
        TileChangeLog.Since r = log.since(100);
        assertEquals(0, r.seq());
        assertFalse(r.reset());
    }
}
