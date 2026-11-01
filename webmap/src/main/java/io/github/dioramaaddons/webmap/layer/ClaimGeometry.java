package io.github.dioramaaddons.webmap.layer;

import io.github.dioramaaddons.claimsapi.ChunkCoord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Outline of a set of chunks: the edges between a chunk of the set and a chunk outside it, in blocks. */
public final class ClaimGeometry {
    /** A horizontal or vertical segment in block coordinates. */
    public record Segment(int x1, int z1, int x2, int z2) {}

    private ClaimGeometry() {}

    public static List<Segment> outline(Collection<Long> chunks) {
        Set<Long> set = new HashSet<>(chunks);
        List<Segment> horizontal = new ArrayList<>();
        List<Segment> vertical = new ArrayList<>();
        for (long c : set) {
            int x = ChunkCoord.unpackX(c);
            int z = ChunkCoord.unpackZ(c);
            int bx = x * 16;
            int bz = z * 16;
            if (!set.contains(ChunkCoord.pack(x, z - 1))) {
                horizontal.add(new Segment(bx, bz, bx + 16, bz));
            }
            if (!set.contains(ChunkCoord.pack(x, z + 1))) {
                horizontal.add(new Segment(bx, bz + 16, bx + 16, bz + 16));
            }
            if (!set.contains(ChunkCoord.pack(x - 1, z))) {
                vertical.add(new Segment(bx, bz, bx, bz + 16));
            }
            if (!set.contains(ChunkCoord.pack(x + 1, z))) {
                vertical.add(new Segment(bx + 16, bz, bx + 16, bz + 16));
            }
        }
        List<Segment> out = new ArrayList<>(mergeHorizontal(horizontal));
        out.addAll(mergeVertical(vertical));
        return out;
    }

    private static List<Segment> mergeHorizontal(List<Segment> segs) {
        segs.sort((a, b) -> a.z1() != b.z1() ? Integer.compare(a.z1(), b.z1()) : Integer.compare(a.x1(), b.x1()));
        List<Segment> out = new ArrayList<>();
        Segment cur = null;
        for (Segment s : segs) {
            if (cur != null && cur.z1() == s.z1() && cur.x2() == s.x1()) {
                cur = new Segment(cur.x1(), cur.z1(), s.x2(), cur.z2());
            } else {
                if (cur != null) {
                    out.add(cur);
                }
                cur = s;
            }
        }
        if (cur != null) {
            out.add(cur);
        }
        return out;
    }

    private static List<Segment> mergeVertical(List<Segment> segs) {
        segs.sort((a, b) -> a.x1() != b.x1() ? Integer.compare(a.x1(), b.x1()) : Integer.compare(a.z1(), b.z1()));
        List<Segment> out = new ArrayList<>();
        Segment cur = null;
        for (Segment s : segs) {
            if (cur != null && cur.x1() == s.x1() && cur.z2() == s.z1()) {
                cur = new Segment(cur.x1(), cur.z1(), cur.x2(), s.z2());
            } else {
                if (cur != null) {
                    out.add(cur);
                }
                cur = s;
            }
        }
        if (cur != null) {
            out.add(cur);
        }
        return out;
    }
}
