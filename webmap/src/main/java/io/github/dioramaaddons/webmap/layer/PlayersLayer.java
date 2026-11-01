package io.github.dioramaaddons.webmap.layer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Player positions, only when enabled in the config. Spectators are never shown. */
public final class PlayersLayer {
    private final boolean enabled;
    private final int intervalTicks;
    private volatile String json;

    public PlayersLayer(boolean enabled, int intervalSeconds) {
        this.enabled = enabled;
        this.intervalTicks = intervalSeconds * 20;
        this.json = disabledJson(enabled);
    }

    private static String disabledJson(boolean enabled) {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", enabled);
        o.add("players", new JsonArray());
        return o.toString();
    }

    public boolean enabled() {
        return enabled;
    }

    /** Server thread. */
    public void tick(MinecraftServer server) {
        if (!enabled || server.getTickCount() % intervalTicks != 0) {
            return;
        }
        JsonArray players = new JsonArray();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) {
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("name", p.getGameProfile().getName());
            o.addProperty("dimension", p.level().dimension().location().toString());
            o.addProperty("x", Math.round(p.getX()));
            o.addProperty("y", Math.round(p.getY()));
            o.addProperty("z", Math.round(p.getZ()));
            players.add(o);
        }
        JsonObject o = new JsonObject();
        o.addProperty("enabled", true);
        o.add("players", players);
        json = o.toString();
    }

    public String json() {
        return json;
    }
}
