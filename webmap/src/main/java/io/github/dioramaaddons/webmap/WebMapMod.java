package io.github.dioramaaddons.webmap;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(WebMapMod.MOD_ID)
public final class WebMapMod {
    public static final String MOD_ID = "dioaddons_webmap";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WebMapMod(IEventBus modEventBus, ModContainer container) {
        LOGGER.info("Diorama Addons: Web Map {} loaded", container.getModInfo().getVersion());
    }
}
