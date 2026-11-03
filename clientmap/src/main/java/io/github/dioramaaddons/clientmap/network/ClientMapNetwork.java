package io.github.dioramaaddons.clientmap.network;

import io.github.dioramaaddons.clientmap.server.ClientMapServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** All payloads are optional: vanilla clients and clients without clientmap can join. */
public final class ClientMapNetwork {
    public static final int PROTOCOL = 2;

    private ClientMapNetwork() {}

    private static ServerPlayer player(IPayloadContext ctx) {
        return ctx.player() instanceof ServerPlayer sp ? sp : null;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(Integer.toString(PROTOCOL)).optional();
        r.playToServer(CmPayloads.Hello.TYPE, CmPayloads.Hello.CODEC, (p, ctx) -> {
            ClientMapServer s = ClientMapServer.get();
            if (s != null && player(ctx) != null && p.protocol() == PROTOCOL) {
                s.onHello(player(ctx));
            }
        });
        r.playToServer(CmPayloads.ExploredRequest.TYPE, CmPayloads.ExploredRequest.CODEC, (p, ctx) -> {
            ClientMapServer s = ClientMapServer.get();
            if (s != null && player(ctx) != null) {
                s.onExploredRequest(player(ctx), p);
            }
        });
        r.playToServer(CmPayloads.ShareWaypoint.TYPE, CmPayloads.ShareWaypoint.CODEC, (p, ctx) -> {
            ClientMapServer s = ClientMapServer.get();
            ServerPlayer player = player(ctx);
            if (s != null && player != null) {
                ClientMapServer.WaypointResult res = s.shareWaypoint(player, p);
                if (res != ClientMapServer.WaypointResult.SUCCESS) {
                    feedback(player, res, "", "");
                }
            }
        });
        r.playToServer(CmPayloads.Preferences.TYPE, CmPayloads.Preferences.CODEC, (p, ctx) -> {
            ClientMapServer s = ClientMapServer.get();
            ServerPlayer player = player(ctx);
            if (s != null && player != null) {
                s.setPreferences(player, p.visible(), p.shareExplored());
            }
        });
        r.playToServer(CmPayloads.DeleteWaypoint.TYPE, CmPayloads.DeleteWaypoint.CODEC, (p, ctx) -> {
            ClientMapServer s = ClientMapServer.get();
            ServerPlayer player = player(ctx);
            if (s != null && player != null) {
                feedback(player, s.deleteWaypoint(player, p.id()), "dioaddons_clientmap.waypoint.removed", "");
            }
        });
        r.playToClient(CmPayloads.Session.TYPE, CmPayloads.Session.CODEC,
                (p, ctx) -> io.github.dioramaaddons.clientmap.client.ClientMapClient.onSession(p));
        r.playToClient(CmPayloads.Claims.TYPE, CmPayloads.Claims.CODEC,
                (p, ctx) -> io.github.dioramaaddons.clientmap.client.ClientMapClient.onClaims(p));
        r.playToClient(CmPayloads.Waypoints.TYPE, CmPayloads.Waypoints.CODEC,
                (p, ctx) -> io.github.dioramaaddons.clientmap.client.ClientMapClient.onWaypoints(p));
        r.playToClient(CmPayloads.Players.TYPE, CmPayloads.Players.CODEC,
                (p, ctx) -> io.github.dioramaaddons.clientmap.client.ClientMapClient.onPlayers(p));
        r.playToClient(CmPayloads.ExploredChunks.TYPE, CmPayloads.ExploredChunks.CODEC,
                (p, ctx) -> io.github.dioramaaddons.clientmap.client.ClientMapClient.onExplored(p));
    }

    private static void feedback(ServerPlayer player, ClientMapServer.WaypointResult r, String okKey, String arg) {
        String key = r == ClientMapServer.WaypointResult.SUCCESS ? okKey : "dioaddons_clientmap.waypoint." + r.name().toLowerCase();
        player.displayClientMessage(io.github.dioramaaddons.clientmap.server.ServerLang.tr(player, key, arg), true);
    }
}
