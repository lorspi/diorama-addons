package io.github.dioramaaddons.claimsapi;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(ClaimsApiMod.MOD_ID)
public final class ClaimsApiMod {
    public static final String MOD_ID = "dioaddons_claims_api";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ClaimsApiMod(IEventBus modEventBus, ModContainer container) {
        LOGGER.info("Diorama Addons: Claims API {} loaded", container.getModInfo().getVersion());
    }
}
