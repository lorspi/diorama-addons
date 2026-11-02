package io.github.dioramaaddons.webmap.client;

import io.github.dioramaaddons.webmap.network.ConfigRequestPayload;
import io.github.dioramaaddons.webmap.network.ConfigStatePayload;
import io.github.dioramaaddons.webmap.network.ConfigUpdatePayload;
import io.github.dioramaaddons.webmap.network.WebSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Web map settings for operators: title, icon and which layers the page shows. The values live on the server
 * (its config file); this screen only asks for them and sends changes, which the server checks again.
 * Opened from the mod list ("Config") or with {@code /webmap config}.
 */
public final class WebMapConfigScreen extends Screen {
    private static final String P = "dioaddons_webmap.gui.";
    private static final int ROW = 24;
    private static final int WIDTH = 310;

    private final @Nullable Screen parent;
    private @Nullable ConfigStatePayload state;
    /** Shown instead of the form: loading, not connected, denied. */
    private Component notice = Component.translatable(P + "loading");
    private @Nullable Component status;
    private final List<AbstractWidget> form = new ArrayList<>();
    private EditBox title;
    private EditBox favicon;
    private CycleButton<Boolean> claims;
    private CycleButton<Boolean> players;
    private CycleButton<Boolean> online;
    private CycleButton<Boolean> chat;
    private boolean requested;
    private int statusY;

    public WebMapConfigScreen(@Nullable Screen parent) {
        super(Component.translatable(P + "title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        form.clear();
        WebSettings s = state == null ? new WebSettings("", "", true, false, true, false)
                : current(state.settings());
        int left = (width - WIDTH) / 2;
        int y = 46;
        title = addForm(new EditBox(font, left, y + 10, WIDTH, 20, Component.translatable(P + "page_title")));
        title.setMaxLength(WebSettings.MAX_TITLE);
        title.setValue(s.title());
        title.setHint(Component.translatable(P + "page_title.hint").withStyle(ChatFormatting.DARK_GRAY));
        y += ROW + 10;
        favicon = addForm(new EditBox(font, left, y + 10, WIDTH, 20, Component.translatable(P + "favicon")));
        favicon.setMaxLength(WebSettings.MAX_FAVICON);
        favicon.setValue(s.favicon());
        favicon.setHint(Component.translatable(P + "favicon.hint").withStyle(ChatFormatting.DARK_GRAY));
        favicon.setTooltip(Tooltip.create(Component.translatable(P + "favicon.tooltip")));
        y += ROW + 16;
        int half = (WIDTH - 10) / 2;
        claims = addForm(toggle(left, y, half, "claims", s.claims()));
        players = addForm(toggle(left + half + 10, y, half, "players", s.players()));
        y += ROW;
        online = addForm(toggle(left, y, half, "online", s.online()));
        chat = addForm(toggle(left + half + 10, y, half, "chat", s.chat()));
        if (state != null && !state.claimsAvailable()) {
            claims.setTooltip(Tooltip.create(Component.translatable(P + "claims.missing")));
        }
        y += ROW + 12;
        addForm(Button.builder(Component.translatable(P + "save"), b -> save()).bounds(left, y, half, 20).build());
        statusY = y + 28;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(left + half + 10, y, half, 20).build());
        boolean ready = state != null && state.reason() != ConfigStatePayload.Reason.DENIED;
        for (AbstractWidget w : form) {
            w.visible = ready;
        }
        if (!requested) {
            requested = true;
            request();
        }
    }

    /** Values typed so far survive a resize; otherwise the server's. */
    private WebSettings current(WebSettings fromServer) {
        if (title == null) {
            return fromServer;
        }
        return new WebSettings(title.getValue(), favicon.getValue(), claims.getValue(), players.getValue(),
                online.getValue(), chat.getValue());
    }

    private <T extends AbstractWidget> T addForm(T widget) {
        form.add(widget);
        return addRenderableWidget(widget);
    }

    private CycleButton<Boolean> toggle(int x, int y, int w, String key, boolean value) {
        CycleButton<Boolean> b = CycleButton.onOffBuilder(value)
                .create(x, y, w, 20, Component.translatable(P + key));
        b.setTooltip(Tooltip.create(Component.translatable(P + key + ".tooltip")));
        return b;
    }

    private void request() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            notice = Component.translatable(P + "offline");
        } else if (!mc.getConnection().hasChannel(ConfigRequestPayload.TYPE)) {
            notice = Component.translatable(P + "server_missing");
        } else {
            PacketDistributor.sendToServer(ConfigRequestPayload.INSTANCE);
        }
    }

    private void save() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || !mc.getConnection().hasChannel(ConfigUpdatePayload.TYPE)) {
            return;
        }
        PacketDistributor.sendToServer(new ConfigUpdatePayload(new WebSettings(title.getValue(), favicon.getValue(),
                claims.getValue(), players.getValue(), online.getValue(), chat.getValue())));
        status = Component.translatable(P + "saving").withStyle(ChatFormatting.GRAY);
    }

    /** Settings from the server. */
    void accept(ConfigStatePayload p) {
        switch (p.reason()) {
            case DENIED -> {
                notice = Component.translatable(P + "denied");
                state = p;
            }
            case SAVED -> {
                state = p;
                status = Component.translatable(P + "saved").withStyle(ChatFormatting.GREEN);
                title = null; // take the server's (cleaned) values
            }
            default -> {
                state = p;
                title = null;
            }
        }
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, getTitle(), width / 2, 14, 0xFFFFFF);
        int left = (width - WIDTH) / 2;
        if (state == null || state.reason() == ConfigStatePayload.Reason.DENIED) {
            g.drawCenteredString(font, notice, width / 2, height / 2 - 20, 0xFFCC66);
            return;
        }
        Component where = state.address().isEmpty()
                ? Component.translatable(P + "http_off").withStyle(ChatFormatting.RED)
                : Component.translatable(P + "address", "http://" + state.address() + "/").withStyle(ChatFormatting.GRAY);
        g.drawCenteredString(font, where, width / 2, 28, 0xAAAAAA);
        g.drawString(font, Component.translatable(P + "page_title"), left, 46, 0xE0E0E0);
        g.drawString(font, Component.translatable(P + "favicon"), left, 46 + ROW + 10, 0xE0E0E0);
        if (status != null) {
            g.drawCenteredString(font, status, width / 2, statusY, 0xFFFFFF);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
