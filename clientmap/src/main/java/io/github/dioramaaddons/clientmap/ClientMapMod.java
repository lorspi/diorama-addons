package io.github.dioramaaddons.clientmap;

import com.mojang.logging.LogUtils;
import io.github.dioramaaddons.clientmap.config.ClientMapClientConfig;
import io.github.dioramaaddons.clientmap.config.ClientMapConfig;
import io.github.dioramaaddons.clientmap.network.ClientMapNetwork;
import io.github.dioramaaddons.clientmap.server.ClientMapCommands;
import io.github.dioramaaddons.clientmap.server.ClientMapServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Diorama integration. The server part (explored map, shared locations, groups, claims feed) runs on any
 * server; the client part only with a supported Diorama.
 */
@Mod(ClientMapMod.MOD_ID)
public final class ClientMapMod {
    public static final String MOD_ID = "dioaddons_clientmap";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ClientMapMod(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, ClientMapConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientMapClientConfig.SPEC);
        modEventBus.addListener(ClientMapNetwork::register);
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientMapCommands::register);
        bus.addListener((ServerStartedEvent e) -> ClientMapServer.start(e.getServer()));
        bus.addListener((ServerStoppedEvent e) -> ClientMapServer.stop());
        bus.addListener((ServerTickEvent.Post e) -> {
            ClientMapServer s = ClientMapServer.get();
            if (s != null) {
                s.tick();
            }
        });
        bus.addListener((ChunkWatchEvent.Watch e) -> {
            ClientMapServer s = ClientMapServer.get();
            if (s != null) {
                s.markExplored(e.getPlayer(), e.getLevel(), e.getPos());
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            ClientMapServer s = ClientMapServer.get();
            if (s != null && e.getEntity() instanceof ServerPlayer p) {
                s.onLogin(p);
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            ClientMapServer s = ClientMapServer.get();
            if (s != null && e.getEntity() instanceof ServerPlayer p) {
                s.onLogout(p);
            }
        });
        if (FMLEnvironment.dist.isClient()) {
            io.github.dioramaaddons.clientmap.client.ClientMapClient.init(modEventBus, container);
        }
        LOGGER.info("Diorama Addons: Client Map {} loaded", container.getModInfo().getVersion());
    }
}
