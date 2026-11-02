package io.github.dioramaaddons.webmap.layer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Online players: positions on the map and the list of connected players, each published only when enabled
 * in the config. Spectators never get a position. The settings are read every update, so config changes
 * apply without a restart.
 */
public final class PlayersLayer {
    private volatile String json = build(false, false, new JsonArray(), new JsonArray(), 0);

    /**
     * Server thread.
     *
     * @param positions publish names and positions
     * @param online publish the names of connected players
     */
    public void tick(MinecraftServer server, boolean positions, boolean online, int intervalSeconds) {
        if (server.getTickCount() % (intervalSeconds * 20) != 0) {
            return;
        }
        JsonArray players = new JsonArray();
        JsonArray names = new JsonArray();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (online) {
                names.add(p.getGameProfile().getName());
            }
            if (!positions || p.isSpectator()) {
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("name", p.getGameProfile().getName());
            o.addProperty("dimension", p.level().dimension().location().toString());
            o.addProperty("x", Math.round(p.getX()));
            o.addProperty("y", Math.round(p.getY()));
            o.addProperty("z", Math.round(p.getZ()));
            o.addProperty("yaw", Math.round(p.getYRot()));
            players.add(o);
        }
        json = build(positions, online, players, names, online ? server.getMaxPlayers() : 0);
    }

    private static String build(boolean positions, boolean online, JsonArray players, JsonArray names, int max) {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", positions);
        o.add("players", players);
        o.addProperty("onlineEnabled", online);
        o.add("online", names);
        o.addProperty("max", max);
        return o.toString();
    }

    public String json() {
        return json;
    }
}
