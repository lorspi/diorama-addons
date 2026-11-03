package io.github.dioramaaddons.clientmap.client;

import io.github.dioramaaddons.clientmap.config.ClientMapClientConfig;
import io.github.dioramaaddons.clientmap.core.ShareMode;
import io.github.dioramaaddons.clientmap.network.CmPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The player's map settings: claims on the map and on the minimap (kept on this client), and whether their location
 * and what they explore are shared (kept on the server, so only while connected to a server with Client Map).
 * Opened from the map's right-click menu, {@code /clientmap settings} and the mods list.
 */
public final class MapSettingsScreen extends Screen {
    private static final int W = 240;

    private final @Nullable Screen parent;

    public MapSettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("dioaddons_clientmap.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = (width - W) / 2;
        int y = Math.max(36, height / 2 - 70);
        addRenderableWidget(toggle("claims_on_map", ClientMapClientConfig.flag(ClientMapClientConfig.CLAIMS_ON_MAP), true,
                x, y, v -> ClientMapClientConfig.set(ClientMapClientConfig.CLAIMS_ON_MAP, v)));
        y += 24;
        addRenderableWidget(toggle("claims_on_minimap", ClientMapClientConfig.flag(ClientMapClientConfig.CLAIMS_ON_MINIMAP),
                true, x, y, v -> ClientMapClientConfig.set(ClientMapClientConfig.CLAIMS_ON_MINIMAP, v)));
        y += 24;
        CmPayloads.Session s = ClientMapClient.session();
        boolean server = s != null && Minecraft.getInstance().getConnection() != null
                && Minecraft.getInstance().getConnection().hasChannel(CmPayloads.Preferences.TYPE);
        boolean locations = server && s.locationsMode() != ShareMode.DISABLED;
        boolean explored = server && s.exploredMode() != ShareMode.DISABLED;
        addRenderableWidget(toggle("share_location", s == null || s.visible(), locations, x, y,
                v -> send(v, ClientMapClient.session() == null || ClientMapClient.session().shareExplored())));
        y += 24;
        addRenderableWidget(toggle("share_explored", s == null || s.shareExplored(), explored, x, y,
                v -> send(ClientMapClient.session() == null || ClientMapClient.session().visible(), v)));
        y += 32;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + W / 2 - 50, y, 100, 20)
                .build());
    }

    private interface Change {
        void set(boolean value);
    }

    private CycleButton<Boolean> toggle(String key, boolean value, boolean active, int x, int y, Change change) {
        CycleButton<Boolean> b = CycleButton.onOffBuilder(value)
                .withTooltip(v -> Tooltip.create(Component.translatable("dioaddons_clientmap.settings." + key + ".tooltip")))
                .create(x, y, W, 20, Component.translatable("dioaddons_clientmap.settings." + key), (btn, v) -> change.set(v));
        b.active = active;
        if (!active) {
            b.setTooltip(Tooltip.create(Component.translatable("dioaddons_clientmap.settings.server_off")));
        }
        return b;
    }

    private static void send(boolean visible, boolean shareExplored) {
        PacketDistributor.sendToServer(new CmPayloads.Preferences(visible, shareExplored));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, Math.max(36, height / 2 - 70) - 20, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
