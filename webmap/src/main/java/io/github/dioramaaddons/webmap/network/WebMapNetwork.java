package io.github.dioramaaddons.webmap.network;

import io.github.dioramaaddons.webmap.WebMapMod;
import io.github.dioramaaddons.webmap.WebMapService;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Optional payloads for the in-game settings screen. The web map is server-side: players without the mod join
 * as usual, and only operators who have it can open the screen. Client handlers are reached through lambdas,
 * so client classes are only loaded on the client. Handlers run on the main thread (registrar default).
 */
public final class WebMapNetwork {
    public static final String VERSION = "1";

    private WebMapNetwork() {}

    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WebMapMod.MOD_ID, path));
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(VERSION).optional();
        r.playToServer(ConfigRequestPayload.TYPE, ConfigRequestPayload.CODEC, WebMapNetwork::request);
        r.playToServer(ConfigUpdatePayload.TYPE, ConfigUpdatePayload.CODEC, WebMapNetwork::update);
        r.playToClient(ConfigStatePayload.TYPE, ConfigStatePayload.CODEC,
                (p, ctx) -> io.github.dioramaaddons.webmap.client.WebMapClient.state(p));
    }

    private static void request(ConfigRequestPayload p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            sendState(player, ConfigStatePayload.Reason.STATE);
        }
    }

    private static void update(ConfigUpdatePayload p, IPayloadContext ctx) {
        WebMapService s = WebMapService.get();
        if (!(ctx.player() instanceof ServerPlayer player) || s == null) {
            return;
        }
        if (!s.canConfigure(player)) {
            sendState(player, ConfigStatePayload.Reason.DENIED);
            return;
        }
        s.saveSettings(p.settings().sanitized(), player.getGameProfile().getName());
        sendState(player, ConfigStatePayload.Reason.SAVED);
    }

    /** Sends the settings, or DENIED to players who may not see them. Returns false if the player lacks the mod. */
    public static boolean sendState(ServerPlayer player, ConfigStatePayload.Reason reason) {
        WebMapService s = WebMapService.get();
        if (!player.connection.hasChannel(ConfigStatePayload.TYPE)) {
            return false;
        }
        if (s == null || !s.canConfigure(player)) {
            PacketDistributor.sendToPlayer(player, new ConfigStatePayload(ConfigStatePayload.Reason.DENIED,
                    new WebSettings("", "", false, false, false, false), false, ""));
            return true;
        }
        PacketDistributor.sendToPlayer(player, new ConfigStatePayload(reason, s.currentSettings(), s.claimsProviderPresent(),
                s.address()));
        return true;
    }
}
