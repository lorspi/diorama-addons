package io.github.dioramaaddons.webmap.client;

import io.github.dioramaaddons.webmap.network.ConfigStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only setup. Only referenced behind a {@code Dist.CLIENT} check or from client-bound handlers. */
public final class WebMapClient {
    private WebMapClient() {}

    public static void init(ModContainer container) {
        // "Config" button in the mod list.
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (mod, parent) -> new WebMapConfigScreen(parent));
    }

    /** Main thread: settings arrived from the server. */
    public static void state(ConfigStatePayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof WebMapConfigScreen screen) {
            screen.accept(p);
            return;
        }
        if (p.reason() == ConfigStatePayload.Reason.OPEN) {
            WebMapConfigScreen screen = new WebMapConfigScreen(null);
            mc.setScreen(screen);
            screen.accept(p);
        } else if (p.reason() == ConfigStatePayload.Reason.DENIED && mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("dioaddons_webmap.gui.denied"), false);
        }
    }
}
