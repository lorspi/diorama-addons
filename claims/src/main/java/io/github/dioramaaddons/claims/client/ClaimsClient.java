package io.github.dioramaaddons.claims.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.dioramaaddons.claims.network.RequestStatePayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Client-only setup. Only referenced behind a {@code Dist.CLIENT} check. */
public final class ClaimsClient {
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.dioaddons_claims.open", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.dioaddons_claims");

    private ClaimsClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(OPEN_KEY));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            while (OPEN_KEY.consumeClick()) {
                openScreen();
            }
        });
    }

    public static void openScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }
        if (!mc.getConnection().hasChannel(RequestStatePayload.TYPE)) {
            mc.player.displayClientMessage(Component.translatable("dioaddons_claims.gui.server_missing"), true);
            return;
        }
        mc.setScreen(new ClaimsScreen());
    }
}
