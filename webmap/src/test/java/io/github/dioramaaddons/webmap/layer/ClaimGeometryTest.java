package io.github.dioramaaddons.webmap.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dioramaaddons.claimsapi.ChunkCoord;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimGeometryTest {
    @Test
    void singleChunkHasFourEdges() {
        List<ClaimGeometry.Segment> s = ClaimGeometry.outline(List.of(ChunkCoord.pack(0, 0)));
        assertEquals(4, s.size());
        assertTrue(s.contains(new ClaimGeometry.Segment(0, 0, 16, 0)));
        assertTrue(s.contains(new ClaimGeometry.Segment(0, 16, 16, 16)));
    }

    @Test
    void rectangleIsMergedIntoFourSides() {
        List<Long> chunks = List.of(ChunkCoord.pack(-1, 2), ChunkCoord.pack(0, 2), ChunkCoord.pack(-1, 3), ChunkCoord.pack(0, 3));
        List<ClaimGeometry.Segment> s = ClaimGeometry.outline(chunks);
        assertEquals(4, s.size());
        assertTrue(s.contains(new ClaimGeometry.Segment(-16, 32, 16, 32)));
        assertTrue(s.contains(new ClaimGeometry.Segment(16, 32, 16, 64)));
    }

    @Test
    void lShapeHasSixSides() {
        List<Long> chunks = List.of(ChunkCoord.pack(0, 0), ChunkCoord.pack(1, 0), ChunkCoord.pack(0, 1));
        assertEquals(6, ClaimGeometry.outline(chunks).size());
    }

    @Test
    void ownerColourIsStable() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertEquals(OwnerColors.hex(id), OwnerColors.hex(id));
        assertTrue(OwnerColors.hex(id).matches("#[0-9a-f]{6}"));
    }
}
