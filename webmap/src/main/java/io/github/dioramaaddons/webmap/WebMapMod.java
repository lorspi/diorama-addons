package io.github.dioramaaddons.webmap;

import com.mojang.logging.LogUtils;
import io.github.dioramaaddons.webmap.config.WebMapConfig;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Server-side web map. Players do not need to install anything. */
@Mod(WebMapMod.MOD_ID)
public final class WebMapMod {
    public static final String MOD_ID = "dioaddons_webmap";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WebMapMod(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, WebMapConfig.SPEC);
        // The config file is watched: apply page/layer settings and restart HTTP when its settings change.
        modEventBus.addListener((ModConfigEvent.Reloading e) -> {
            WebMapService s = WebMapService.get();
            if (s != null && e.getConfig().getSpec() == WebMapConfig.SPEC) {
                s.applyConfig();
            }
        });
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(WebMapCommand::register);
        bus.addListener((ServerStartedEvent e) -> WebMapService.start(e.getServer()));
        bus.addListener((ServerStoppingEvent e) -> WebMapService.stop());
        bus.addListener((ServerTickEvent.Post e) -> {
            WebMapService s = WebMapService.get();
            if (s != null) {
                s.tick();
            }
        });
        // Public chat for the web page: last in line and only when no other mod cancelled the message.
        bus.addListener(EventPriority.LOWEST, false, ServerChatEvent.class, e -> {
            WebMapService s = WebMapService.get();
            if (s != null) {
                s.onChat(e.getUsername(), e.getRawText());
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> joinLeave(e.getEntity().getGameProfile().getName(), true));
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> joinLeave(e.getEntity().getGameProfile().getName(), false));
        // Re-render chunks after they are written to disk (autosave) or unloaded.
        bus.addListener((ChunkDataEvent.Save e) -> mark(e.getLevel(), e.getChunk().getPos().x, e.getChunk().getPos().z));
        bus.addListener((ChunkEvent.Unload e) -> mark(e.getLevel(), e.getChunk().getPos().x, e.getChunk().getPos().z));
        LOGGER.info("Diorama Addons: Web Map {} loaded", container.getModInfo().getVersion());
    }

    private static void joinLeave(String name, boolean joined) {
        WebMapService s = WebMapService.get();
        if (s != null) {
            s.onJoinLeave(name, joined);
        }
    }

    private static void mark(Object level, int chunkX, int chunkZ) {
        WebMapService s = WebMapService.get();
        if (s != null && level instanceof ServerLevel sl) {
            s.markChunk(sl, chunkX, chunkZ);
        }
    }
}
