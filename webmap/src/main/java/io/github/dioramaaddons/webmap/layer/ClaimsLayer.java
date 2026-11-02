package io.github.dioramaaddons.webmap.layer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.dioramaaddons.claimsapi.ChunkCoord;
import io.github.dioramaaddons.claimsapi.ClaimInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Claim layer. Claims come from Diorama Addons: Claims (through claims-api, bundled in this jar) and from
 * Mochos Esenciales (read by reflection, see {@link MochosClaimSource}); either, both or none may be installed.
 * Only names, owners and chunks are published: neither API exposes members or permissions.
 */
public final class ClaimsLayer {
    /** Ticks between checks for a claims provider that started or stopped. */
    private static final int REFRESH_TICKS = 100;

    private final List<ClaimSource> sources = new ArrayList<>();
    private volatile boolean available;
    private volatile Collection<ClaimInfo> snapshot = List.of();
    private volatile long version;
    private String json = "[]";
    private long jsonVersion = -1;
    private int ticks;

    /** Server thread. */
    public void start() {
        sources.add(new ApiClaimSource());
        MochosClaimSource mochos = MochosClaimSource.create();
        if (mochos != null) {
            sources.add(mochos);
        }
        refresh();
    }

    public void stop() {
        for (ClaimSource s : sources) {
            s.stop();
        }
    }

    public boolean available() {
        return available;
    }

    /** Re-checks the providers; returns true when the layer became available or unavailable. */
    private boolean refresh() {
        boolean any = false;
        for (ClaimSource s : sources) {
            any |= s.refresh();
        }
        boolean changed = any != available;
        available = any;
        return changed;
    }

    /**
     * Server thread: refresh the snapshot after changes.
     *
     * @return true when the layer became available or unavailable (the page settings must be rebuilt)
     */
    public boolean tick() {
        boolean availabilityChanged = ++ticks % REFRESH_TICKS == 0 && refresh();
        boolean dirty = false;
        for (ClaimSource s : sources) {
            dirty |= s.takeDirty();
        }
        if (dirty) {
            List<ClaimInfo> all = new ArrayList<>();
            for (ClaimSource s : sources) {
                if (s.available()) {
                    all.addAll(s.claims());
                }
            }
            snapshot = all;
            version++;
        }
        return availabilityChanged;
    }

    /** Any thread. JSON is rebuilt lazily, only after a change. */
    public synchronized String json() {
        if (jsonVersion == version) {
            return json;
        }
        long v = version;
        JsonArray out = new JsonArray();
        for (ClaimInfo c : snapshot) {
            JsonObject o = new JsonObject();
            o.addProperty("id", c.id().toString());
            o.addProperty("name", c.name());
            o.addProperty("owner", c.ownerName());
            o.addProperty("color", OwnerColors.hex(c.ownerId()));
            o.addProperty("dimension", c.dimension());
            JsonArray chunks = new JsonArray();
            for (long chunk : c.chunks()) {
                JsonArray xz = new JsonArray();
                xz.add(ChunkCoord.unpackX(chunk));
                xz.add(ChunkCoord.unpackZ(chunk));
                chunks.add(xz);
            }
            o.add("chunks", chunks);
            JsonArray outline = new JsonArray();
            for (ClaimGeometry.Segment s : ClaimGeometry.outline(c.chunks())) {
                JsonArray seg = new JsonArray();
                seg.add(s.x1());
                seg.add(s.z1());
                seg.add(s.x2());
                seg.add(s.z2());
                outline.add(seg);
            }
            o.add("outline", outline);
            out.add(o);
        }
        json = out.toString();
        jsonVersion = v;
        return json;
    }
}
