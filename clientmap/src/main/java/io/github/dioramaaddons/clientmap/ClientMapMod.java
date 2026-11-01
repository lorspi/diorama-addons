package io.github.dioramaaddons.clientmap;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(ClientMapMod.MOD_ID)
public final class ClientMapMod {
    public static final String MOD_ID = "dioaddons_clientmap";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ClientMapMod(IEventBus modEventBus, ModContainer container) {
        LOGGER.info("Diorama Addons: Client Map {} loaded", container.getModInfo().getVersion());
    }
}
