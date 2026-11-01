package io.github.dioramaaddons.webmap.layer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.dioramaaddons.claimsapi.ChunkCoord;
import io.github.dioramaaddons.claimsapi.ClaimChangeListener;
import io.github.dioramaaddons.claimsapi.ClaimInfo;
import io.github.dioramaaddons.claimsapi.ClaimsApi;
import io.github.dioramaaddons.claimsapi.ClaimsApiProvider;
import java.util.Collection;
import java.util.List;

/**
 * Claim layer fed by claims-api. The API is bundled in this jar, so these classes always load; when the
 * claims mod is not installed no implementation is registered and the layer simply stays unavailable.
 * Only names, owners and chunks are published: the API does not expose members or permissions.
 */
public final class ClaimsLayer {
    private final ClaimChangeListener listener = change -> dirty = true;
    private ClaimsApi api;
    private volatile boolean dirty = true;
    private volatile Collection<ClaimInfo> snapshot = List.of();
    private volatile long version;
    private String json = "[]";
    private long jsonVersion = -1;

    /** Server thread. */
    public void start() {
        api = ClaimsApiProvider.get().orElse(null);
        if (api != null) {
            api.addListener(listener);
        }
    }

    public void stop() {
        if (api != null) {
            api.removeListener(listener);
        }
    }

    public boolean available() {
        return api != null;
    }

    /** Server thread: refresh the snapshot after changes. */
    public void tick() {
        if (api != null && dirty) {
            dirty = false;
            snapshot = api.allClaims();
            version++;
        }
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
