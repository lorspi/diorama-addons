package io.github.dioramaaddons.claims;

import com.mojang.logging.LogUtils;
import io.github.dioramaaddons.claims.command.ClaimsCommands;
import io.github.dioramaaddons.claims.config.ClaimsConfig;
import io.github.dioramaaddons.claims.network.ClaimsNetwork;
import io.github.dioramaaddons.claims.protection.ProtectionHandler;
import io.github.dioramaaddons.claims.server.ClaimsService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(ClaimsMod.MOD_ID)
public final class ClaimsMod {
    public static final String MOD_ID = "dioaddons_claims";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ClaimsMod(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ClaimsConfig.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClaimsConfig.CLIENT_SPEC);
        modEventBus.addListener(ClaimsNetwork::register);

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(ClaimsCommands::register);
        bus.addListener((ServerStartingEvent e) -> ClaimsService.start(e.getServer()));
        bus.addListener((ServerStoppedEvent e) -> ClaimsService.stop());
        bus.addListener((ServerTickEvent.Post e) -> {
            ClaimsService s = ClaimsService.get();
            if (s != null) {
                s.tick();
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            ClaimsService s = ClaimsService.get();
            if (s != null && e.getEntity() instanceof ServerPlayer p) {
                s.onPlayerLogin(p);
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            ClaimsService s = ClaimsService.get();
            if (s != null && e.getEntity() instanceof ServerPlayer p) {
                s.onPlayerLogout(p);
            }
        });
        ProtectionHandler.register(bus);

        if (FMLEnvironment.dist.isClient()) {
            io.github.dioramaaddons.claims.client.ClaimsClient.init(modEventBus);
        }
        LOGGER.info("Diorama Addons: Claims {} loaded", container.getModInfo().getVersion());
    }
}
