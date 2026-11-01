package io.github.dioramaaddons.claims;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(ClaimsMod.MOD_ID)
public final class ClaimsMod {
    public static final String MOD_ID = "dioaddons_claims";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ClaimsMod(IEventBus modEventBus, ModContainer container) {
        LOGGER.info("Diorama Addons: Claims {} loaded", container.getModInfo().getVersion());
    }
}
