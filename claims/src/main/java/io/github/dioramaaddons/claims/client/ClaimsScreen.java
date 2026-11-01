package io.github.dioramaaddons.claims.client;

import io.github.dioramaaddons.claims.config.ClaimsConfig;
import io.github.dioramaaddons.claims.core.ClaimStore;
import io.github.dioramaaddons.claims.core.Permission;
import io.github.dioramaaddons.claims.core.PermissionTemplate;
import io.github.dioramaaddons.claims.network.ClaimChunksPayload;
import io.github.dioramaaddons.claims.network.EditClaimPayload;
import io.github.dioramaaddons.claims.network.RequestStatePayload;
import io.github.dioramaaddons.claims.network.StatePayload;
import io.github.dioramaaddons.claims.network.UnsubscribePayload;
import io.github.dioramaaddons.claimsapi.ChunkCoord;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Claims GUI: a chunk grid around the player plus a side panel with three tabs. The client only sends
 * requests; the server validates each one and answers with a fresh {@link StatePayload}.
 */
public final class ClaimsScreen extends Screen {
    private enum Tab { CLAIMS, MEMBERS, PUBLIC }

    private static final int MARGIN = 8;
    private static final int PANEL_W = 180;
    private static final int ROW_H = 11;
    private static final int CLAIM_ROWS = 6;
    private static final int MEMBER_ROWS = 3;

    private static final int COLOR_BG = 0xFF161616;
    private static final int COLOR_FREE = 0xFF333333;
    private static final int COLOR_OWN = 0xFF2E8B3A;
    private static final int COLOR_OWN_SELECTED = 0xFF5FD06C;
    private static final int COLOR_MEMBER = 0xFF2F6DB5;
    private static final int COLOR_OTHER = 0xFFA83232;
    private static final int COLOR_ADMIN = 0xFFC7802B;
    private static final int COLOR_ADMIN_SELECTED = 0xFFF0B060;

    private @Nullable StatePayload state;
    private Tab tab = Tab.CLAIMS;
    private @Nullable UUID selectedClaim;
    private @Nullable UUID selectedMember;
    private String nameDraft = "";
    private String memberDraft = "";
    private int onlineIndex;
    private int claimScroll;
    private int memberScroll;
    private boolean confirmDelete;

    // layout, computed in init()
    private int gridX, gridY, cell, size, radius;
    private int panelX, panelY;
    private int claimListY, memberListY;

    // drag selection
    private boolean dragging;
    private int dragStartCol, dragStartRow, dragEndCol, dragEndRow;

    private @Nullable EditBox nameBox;
    private @Nullable EditBox memberBox;

    public ClaimsScreen() {
        super(Component.translatable("dioaddons_claims.gui.title"));
        radius = ClaimsConfig.CLIENT.gridRadius.get();
        send(new RequestStatePayload(radius));
    }

    private static void send(CustomPacketPayload payload) {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(payload);
        }
    }

    void onState(StatePayload newState) {
        this.state = newState;
        this.radius = newState.radius();
        if (selectedClaim != null && detail(selectedClaim) == null) {
            selectedClaim = null;
            selectedMember = null;
        }
        StatePayload.ClaimDetail d = selectedDetail();
        if (d != null && (nameBox == null || !nameBox.isFocused())) {
            nameDraft = d.name();
        }
        if (d != null && selectedMember != null && d.members().stream().noneMatch(m -> m.id().equals(selectedMember))) {
            selectedMember = null;
        }
        confirmDelete = false;
        rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null && connection.hasChannel(UnsubscribePayload.TYPE)) {
            PacketDistributor.sendToServer(UnsubscribePayload.INSTANCE);
        }
        super.removed();
    }

    // ---------------------------------------------------------------- state helpers

    private @Nullable StatePayload.ClaimDetail detail(UUID id) {
        if (state == null || id == null) {
            return null;
        }
        for (StatePayload.ClaimDetail d : state.manageable()) {
            if (d.id().equals(id)) {
                return d;
            }
        }
        return null;
    }

    private @Nullable StatePayload.ClaimDetail selectedDetail() {
        return selectedClaim == null ? null : detail(selectedClaim);
    }

    private @Nullable StatePayload.Member selectedMemberEntry() {
        StatePayload.ClaimDetail d = selectedDetail();
        if (d == null || selectedMember == null) {
            return null;
        }
        return d.members().stream().filter(m -> m.id().equals(selectedMember)).findFirst().orElse(null);
    }

    private void select(@Nullable UUID claimId) {
        selectedClaim = claimId;
        selectedMember = null;
        memberScroll = 0;
        StatePayload.ClaimDetail d = selectedDetail();
        nameDraft = d == null ? "" : d.name();
        confirmDelete = false;
        rebuildWidgets();
    }

    // ---------------------------------------------------------------- layout and widgets

    @Override
    protected void init() {
        size = 2 * radius + 1;
        int top = 26;
        int availW = width - PANEL_W - 3 * MARGIN;
        int availH = height - top - 40;
        cell = Math.max(2, Math.min(availW, availH) / size);
        int gridPx = cell * size;
        gridX = MARGIN + Math.max(0, (availW - gridPx) / 2);
        gridY = top;
        panelX = width - PANEL_W - MARGIN;
        panelY = top;

        int tabW = (PANEL_W - 4) / 3;
        addTab(Tab.CLAIMS, "dioaddons_claims.gui.tab.claims", panelX, tabW);
        addTab(Tab.MEMBERS, "dioaddons_claims.gui.tab.members", panelX + tabW + 2, tabW);
        addTab(Tab.PUBLIC, "dioaddons_claims.gui.tab.public", panelX + 2 * (tabW + 2), tabW);

        nameBox = null;
        memberBox = null;
        if (state == null) {
            return;
        }
        int y = panelY + 20;
        switch (tab) {
            case CLAIMS -> initClaimsTab(y);
            case MEMBERS -> initMembersTab(y);
            case PUBLIC -> initPublicTab(y);
        }
    }

    private void addTab(Tab t, String key, int x, int w) {
        Button b = Button.builder(Component.translatable(key), btn -> {
            tab = t;
            rebuildWidgets();
        }).bounds(x, panelY, w, 16).build();
        b.active = tab != t;
        addRenderableWidget(b);
    }

    private void initClaimsTab(int y) {
        y += 24;
        claimListY = y;
        y += CLAIM_ROWS * ROW_H + 6;
        StatePayload.ClaimDetail d = selectedDetail();
        nameBox = new EditBox(font, panelX, y, PANEL_W - 52, 16, Component.translatable("dioaddons_claims.gui.name"));
        nameBox.setMaxLength(ClaimStore.MAX_NAME_LENGTH);
        nameBox.setValue(nameDraft);
        nameBox.setResponder(v -> nameDraft = v);
        nameBox.active = d != null;
        nameBox.setEditable(d != null);
        addRenderableWidget(nameBox);
        Button rename = Button.builder(Component.translatable("dioaddons_claims.gui.rename"), b -> {
            if (selectedClaim != null) {
                send(new EditClaimPayload(EditClaimPayload.Op.RENAME, selectedClaim, Optional.empty(), nameDraft, 0));
            }
        }).bounds(panelX + PANEL_W - 50, y, 50, 16).build();
        rename.active = d != null;
        addRenderableWidget(rename);
        y += 20;
        Button delete = Button.builder(Component.translatable(confirmDelete
                ? "dioaddons_claims.gui.delete_confirm" : "dioaddons_claims.gui.delete"), b -> {
            if (selectedClaim == null) {
                return;
            }
            if (!confirmDelete) {
                confirmDelete = true;
                rebuildWidgets();
            } else {
                send(new EditClaimPayload(EditClaimPayload.Op.DELETE, selectedClaim, Optional.empty(), "", 0));
                confirmDelete = false;
            }
        }).bounds(panelX, y, PANEL_W, 16).build();
        delete.active = d != null;
        addRenderableWidget(delete);
    }

    private void initMembersTab(int y) {
        StatePayload.ClaimDetail d = selectedDetail();
        if (d == null) {
            return;
        }
        y += 12;
        memberListY = y;
        y += MEMBER_ROWS * ROW_H + 6;
        memberBox = new EditBox(font, panelX, y, PANEL_W - 52, 16, Component.translatable("dioaddons_claims.gui.player_name"));
        memberBox.setMaxLength(16);
        memberBox.setValue(memberDraft);
        memberBox.setResponder(v -> memberDraft = v);
        memberBox.setHint(Component.translatable("dioaddons_claims.gui.player_name").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(memberBox);
        addRenderableWidget(Button.builder(Component.translatable("dioaddons_claims.gui.add"), b -> {
            if (!memberDraft.isBlank()) {
                send(new EditClaimPayload(EditClaimPayload.Op.ADD_MEMBER, d.id(), Optional.empty(), memberDraft.trim(),
                        Permission.toMask(PermissionTemplate.MEMBER.permissions())));
                memberDraft = "";
            }
        }).bounds(panelX + PANEL_W - 50, y, 50, 16).build());
        y += 18;
        List<String> online = state.onlinePlayers();
        if (!online.isEmpty()) {
            String next = online.get(Math.floorMod(onlineIndex, online.size()));
            addRenderableWidget(Button.builder(Component.translatable("dioaddons_claims.gui.online", next), b -> {
                memberDraft = next;
                onlineIndex++;
                rebuildWidgets();
            }).bounds(panelX, y, PANEL_W, 16).build());
            y += 18;
        }
        y += 2;

        StatePayload.Member m = selectedMemberEntry();
        if (m == null) {
            return;
        }
        // Templates, four rows of toggles and Remove: shrink the rows when the screen is short.
        int step = Math.max(12, Math.min(17, (height - 42 - y) / 6));
        int bh = step - 1;
        int tw = (PANEL_W - 4) / 3;
        int i = 0;
        for (PermissionTemplate t : PermissionTemplate.values()) {
            int mask = Permission.toMask(t.permissions());
            Button b = Button.builder(Component.translatable(t.translationKey()), btn -> send(new EditClaimPayload(
                    EditClaimPayload.Op.SET_MEMBER, d.id(), Optional.of(m.id()), "", mask)))
                    .bounds(panelX + i * (tw + 2), y, tw, bh).build();
            b.active = m.mask() != mask;
            addRenderableWidget(b);
            i++;
        }
        y += step + 1;
        y = addToggles(y, Permission.playerPermissions(), m.mask(), step, mask -> send(new EditClaimPayload(
                EditClaimPayload.Op.SET_MEMBER, d.id(), Optional.of(m.id()), "", mask)));
        addRenderableWidget(Button.builder(Component.translatable("dioaddons_claims.gui.remove_member", m.name()),
                b -> send(new EditClaimPayload(EditClaimPayload.Op.REMOVE_MEMBER, d.id(), Optional.of(m.id()), "", 0)))
                .bounds(panelX, y + 1, PANEL_W, bh).build());
    }

    private void initPublicTab(int y) {
        StatePayload.ClaimDetail d = selectedDetail();
        if (d == null) {
            return;
        }
        y += 24;
        y = addToggles(y, Permission.playerPermissions(), d.publicMask(), 17, mask -> send(new EditClaimPayload(
                EditClaimPayload.Op.SET_PUBLIC, d.id(), Optional.empty(), "", mask)));
        y += 12;
        EnumSet<Permission> flags = EnumSet.complementOf(Permission.playerPermissions());
        addToggles(y, flags, d.publicMask(), 17, mask -> send(new EditClaimPayload(
                EditClaimPayload.Op.SET_PUBLIC, d.id(), Optional.empty(), "", mask)));
    }

    /** Two columns of on/off buttons. Returns the y below the last row. */
    private int addToggles(int y, EnumSet<Permission> perms, int currentMask, int step,
            java.util.function.IntConsumer onChange) {
        int w = (PANEL_W - 2) / 2;
        int i = 0;
        for (Permission p : perms) {
            boolean on = (currentMask & p.bit()) != 0;
            MutableComponent label = Component.literal(on ? "✔ " : "✘ ")
                    .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED)
                    .append(Component.translatable(p.translationKey()).withStyle(ChatFormatting.WHITE));
            int x = panelX + (i % 2) * (w + 2);
            int by = y + (i / 2) * step;
            Button b = Button.builder(label, btn -> onChange.accept(currentMask ^ p.bit()))
                    .bounds(x, by, w, step - 1)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.translatable(p.translationKey() + ".desc")))
                    .build();
            addRenderableWidget(b);
            i++;
        }
        return y + ((perms.size() + 1) / 2) * step;
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, gridX, 6, 0xFFFFFF);
        if (state == null) {
            g.drawCenteredString(font, Component.translatable("dioaddons_claims.gui.loading"), width / 2, height / 2, 0xAAAAAA);
            return;
        }
        renderGrid(g, mouseX, mouseY);
        renderPanel(g, mouseX, mouseY);
        renderFooter(g);
        renderGridTooltip(g, mouseX, mouseY);
    }

    private int colorFor(int idx) {
        if (idx < 0) {
            return COLOR_FREE;
        }
        StatePayload.GridClaim c = state.gridClaims().get(idx);
        boolean selected = c.id().equals(selectedClaim);
        return switch (c.relation()) {
            case StatePayload.OWN -> selected ? COLOR_OWN_SELECTED : COLOR_OWN;
            case StatePayload.MEMBER -> COLOR_MEMBER;
            case StatePayload.ADMIN_EDITABLE -> selected ? COLOR_ADMIN_SELECTED : COLOR_ADMIN;
            default -> COLOR_OTHER;
        };
    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        int px = cell * size;
        g.fill(gridX - 1, gridY - 1, gridX + px + 1, gridY + px + 1, COLOR_BG);
        int gap = cell >= 6 ? 1 : 0;
        int[] cells = state.cells();
        int stateSize = 2 * state.radius() + 1;
        for (int row = 0; row < size && row < stateSize; row++) {
            for (int col = 0; col < size && col < stateSize; col++) {
                int x = gridX + col * cell;
                int y = gridY + row * cell;
                g.fill(x, y, x + cell - gap, y + cell - gap, colorFor(cells[row * stateSize + col]));
            }
        }
        // drag selection
        if (dragging) {
            int c0 = Math.min(dragStartCol, dragEndCol);
            int c1 = Math.max(dragStartCol, dragEndCol);
            int r0 = Math.min(dragStartRow, dragEndRow);
            int r1 = Math.max(dragStartRow, dragEndRow);
            g.fill(gridX + c0 * cell, gridY + r0 * cell, gridX + (c1 + 1) * cell, gridY + (r1 + 1) * cell, 0x55FFFF55);
            g.renderOutline(gridX + c0 * cell, gridY + r0 * cell, (c1 - c0 + 1) * cell, (r1 - r0 + 1) * cell, 0xFFFFFF55);
        }
        // current chunk and player position
        int center = state.radius();
        g.renderOutline(gridX + center * cell - 1, gridY + center * cell - 1, cell + 1, cell + 1, 0xFFFFFFFF);
        var player = Minecraft.getInstance().player;
        if (player != null) {
            double fx = (player.getX() - state.centerX() * 16) / 16.0;
            double fz = (player.getZ() - state.centerZ() * 16) / 16.0;
            if (fx >= 0 && fx < 1 && fz >= 0 && fz < 1) {
                int dx = gridX + center * cell + (int) (fx * cell);
                int dz = gridY + center * cell + (int) (fz * cell);
                g.fill(dx - 1, dz - 1, dx + 1, dz + 1, 0xFFFFFFFF);
            }
        }
        g.drawCenteredString(font, "N", gridX + px / 2, gridY - 10, 0xFFAAAAAA);
    }

    private void renderPanel(GuiGraphics g, int mouseX, int mouseY) {
        int y = panelY + 20;
        switch (tab) {
            case CLAIMS -> {
                g.drawString(font, Component.translatable("dioaddons_claims.gui.usage_chunks", state.usedChunks(),
                        state.maxChunks()), panelX, y, 0xFFFFFF);
                g.drawString(font, Component.translatable("dioaddons_claims.gui.usage_claims", state.usedClaims(),
                        state.maxClaims()), panelX, y + 10, 0xFFFFFF);
                if (state.bypass()) {
                    g.drawString(font, Component.translatable("dioaddons_claims.gui.bypass"), panelX + 100, y,
                            0xFFF0B060);
                }
                renderClaimList(g, mouseX, mouseY);
            }
            case MEMBERS -> {
                StatePayload.ClaimDetail d = selectedDetail();
                if (d == null) {
                    g.drawWordWrap(font, Component.translatable("dioaddons_claims.gui.select_claim_first"), panelX, y,
                            PANEL_W, 0xAAAAAA);
                    return;
                }
                g.drawString(font, Component.translatable("dioaddons_claims.gui.members_of", d.name()), panelX, y, 0xFFFFFF);
                renderMemberList(g, d, mouseX, mouseY);
                if (selectedMember == null) {
                    g.drawWordWrap(font, Component.translatable("dioaddons_claims.gui.select_member"), panelX,
                            memberListY + MEMBER_ROWS * ROW_H + 6 + 22 + (state.onlinePlayers().isEmpty() ? 0 : 18),
                            PANEL_W, 0x888888);
                }
            }
            case PUBLIC -> {
                StatePayload.ClaimDetail d = selectedDetail();
                if (d == null) {
                    g.drawWordWrap(font, Component.translatable("dioaddons_claims.gui.select_claim_first"), panelX, y,
                            PANEL_W, 0xAAAAAA);
                    return;
                }
                g.drawString(font, Component.translatable("dioaddons_claims.gui.public_of", d.name()), panelX, y, 0xFFFFFF);
                g.drawString(font, Component.translatable("dioaddons_claims.gui.public_players"), panelX, y + 12, 0xAAAAAA);
                int flagsY = y + 24 + 4 * 17 + 2;
                g.drawString(font, Component.translatable("dioaddons_claims.gui.public_flags"), panelX, flagsY, 0xAAAAAA);
            }
        }
    }

    private List<Optional<StatePayload.ClaimDetail>> claimEntries() {
        List<Optional<StatePayload.ClaimDetail>> list = new ArrayList<>();
        list.add(Optional.empty());
        for (StatePayload.ClaimDetail d : state.manageable()) {
            list.add(Optional.of(d));
        }
        return list;
    }

    private void renderClaimList(GuiGraphics g, int mouseX, int mouseY) {
        int y = claimListY;
        g.fill(panelX - 1, y - 1, panelX + PANEL_W + 1, y + CLAIM_ROWS * ROW_H + 1, 0xAA000000);
        List<Optional<StatePayload.ClaimDetail>> entries = claimEntries();
        claimScroll = Math.max(0, Math.min(claimScroll, entries.size() - CLAIM_ROWS));
        for (int i = 0; i < CLAIM_ROWS && i + claimScroll < entries.size(); i++) {
            Optional<StatePayload.ClaimDetail> e = entries.get(i + claimScroll);
            int ry = y + i * ROW_H;
            boolean selected = e.isEmpty() ? selectedClaim == null : e.get().id().equals(selectedClaim);
            boolean hover = mouseX >= panelX && mouseX < panelX + PANEL_W && mouseY >= ry && mouseY < ry + ROW_H;
            if (selected) {
                g.fill(panelX, ry, panelX + PANEL_W, ry + ROW_H, 0x804080FF);
            } else if (hover) {
                g.fill(panelX, ry, panelX + PANEL_W, ry + ROW_H, 0x40FFFFFF);
            }
            Component text;
            if (e.isEmpty()) {
                text = Component.translatable("dioaddons_claims.gui.auto_claim").withStyle(ChatFormatting.ITALIC);
            } else {
                StatePayload.ClaimDetail d = e.get();
                String dim = d.dimension().substring(d.dimension().indexOf(':') + 1);
                String owner = state.bypass() && !d.ownerName().equals(Minecraft.getInstance().getUser().getName())
                        ? " [" + d.ownerName() + "]" : "";
                text = Component.literal(d.name() + owner + " ")
                        .append(Component.literal("(" + d.chunkCount() + ", " + dim + ")").withStyle(ChatFormatting.GRAY));
            }
            g.drawString(font, font.plainSubstrByWidth(text.getString(), PANEL_W - 4), panelX + 2, ry + 2, 0xFFFFFF);
        }
    }

    private void renderMemberList(GuiGraphics g, StatePayload.ClaimDetail d, int mouseX, int mouseY) {
        int y = memberListY;
        g.fill(panelX - 1, y - 1, panelX + PANEL_W + 1, y + MEMBER_ROWS * ROW_H + 1, 0xAA000000);
        List<StatePayload.Member> members = d.members();
        if (members.isEmpty()) {
            g.drawString(font, Component.translatable("dioaddons_claims.gui.no_members"), panelX + 2, y + 2, 0x888888);
            return;
        }
        memberScroll = Math.max(0, Math.min(memberScroll, members.size() - MEMBER_ROWS));
        for (int i = 0; i < MEMBER_ROWS && i + memberScroll < members.size(); i++) {
            StatePayload.Member m = members.get(i + memberScroll);
            int ry = y + i * ROW_H;
            boolean hover = mouseX >= panelX && mouseX < panelX + PANEL_W && mouseY >= ry && mouseY < ry + ROW_H;
            if (m.id().equals(selectedMember)) {
                g.fill(panelX, ry, panelX + PANEL_W, ry + ROW_H, 0x804080FF);
            } else if (hover) {
                g.fill(panelX, ry, panelX + PANEL_W, ry + ROW_H, 0x40FFFFFF);
            }
            String template = PermissionTemplate.matching(Permission.fromMask(m.mask()))
                    .map(t -> Component.translatable(t.translationKey()).getString())
                    .orElse(Component.translatable("dioaddons_claims.template.custom").getString());
            g.drawString(font, m.name(), panelX + 2, ry + 2, 0xFFFFFF);
            g.drawString(font, template, panelX + PANEL_W - 2 - font.width(template), ry + 2, 0xAAAAAA);
        }
    }

    private void renderFooter(GuiGraphics g) {
        // Below the grid only, so the text never runs under the side panel.
        int width = panelX - 2 * MARGIN;
        StatePayload.Feedback fb = state.feedback();
        if (fb != null) {
            Component msg = Component.translatable(fb.key(), fb.args().toArray());
            g.drawString(font, font.plainSubstrByWidth(msg.getString(), width), MARGIN, height - 34,
                    fb.success() ? 0x80FF80 : 0xFF8080);
        }
        g.drawWordWrap(font, Component.translatable("dioaddons_claims.gui.hint"), MARGIN, height - 23, width, 0x888888);
    }

    private void renderGridTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (dragging) {
            return;
        }
        int col = colAt(mouseX);
        int row = rowAt(mouseY);
        if (col < 0 || row < 0) {
            return;
        }
        int chunkX = state.centerX() - state.radius() + col;
        int chunkZ = state.centerZ() - state.radius() + row;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("dioaddons_claims.gui.chunk", chunkX, chunkZ).withStyle(ChatFormatting.GRAY));
        int idx = state.cells()[row * (2 * state.radius() + 1) + col];
        if (idx < 0) {
            lines.add(Component.translatable("dioaddons_claims.gui.free"));
        } else {
            StatePayload.GridClaim c = state.gridClaims().get(idx);
            lines.add(Component.literal(c.name()).withStyle(ChatFormatting.WHITE));
            lines.add(Component.translatable("dioaddons_claims.gui.owner", c.ownerName()).withStyle(ChatFormatting.GRAY));
        }
        g.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    // ---------------------------------------------------------------- input

    private int colAt(double mouseX) {
        if (state == null || mouseX < gridX) {
            return -1;
        }
        int col = (int) ((mouseX - gridX) / cell);
        return col < 2 * state.radius() + 1 && col < size ? col : -1;
    }

    private int rowAt(double mouseY) {
        if (state == null || mouseY < gridY) {
            return -1;
        }
        int row = (int) ((mouseY - gridY) / cell);
        return row < 2 * state.radius() + 1 && row < size ? row : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (state == null) {
            return false;
        }
        int col = colAt(mouseX);
        int row = rowAt(mouseY);
        if (col >= 0 && row >= 0) {
            if (button == 0) {
                dragging = true;
                dragStartCol = dragEndCol = col;
                dragStartRow = dragEndRow = row;
            } else if (button == 1) {
                int idx = state.cells()[row * (2 * state.radius() + 1) + col];
                if (idx >= 0 && detail(state.gridClaims().get(idx).id()) != null) {
                    select(state.gridClaims().get(idx).id());
                }
            }
            return true;
        }
        if (mouseX >= panelX && mouseX < panelX + PANEL_W) {
            if (tab == Tab.CLAIMS && mouseY >= claimListY && mouseY < claimListY + CLAIM_ROWS * ROW_H) {
                int i = (int) ((mouseY - claimListY) / ROW_H) + claimScroll;
                List<Optional<StatePayload.ClaimDetail>> entries = claimEntries();
                if (i < entries.size()) {
                    select(entries.get(i).map(StatePayload.ClaimDetail::id).orElse(null));
                }
                return true;
            }
            StatePayload.ClaimDetail d = selectedDetail();
            if (tab == Tab.MEMBERS && d != null && mouseY >= memberListY && mouseY < memberListY + MEMBER_ROWS * ROW_H) {
                int i = (int) ((mouseY - memberListY) / ROW_H) + memberScroll;
                if (i < d.members().size()) {
                    selectedMember = d.members().get(i).id();
                    rebuildWidgets();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            int max = Math.min(size, 2 * state.radius() + 1) - 1;
            dragEndCol = Math.max(0, Math.min(max, (int) Math.floor((mouseX - gridX) / cell)));
            dragEndRow = Math.max(0, Math.min(max, (int) Math.floor((mouseY - gridY) / cell)));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            dragging = false;
            applySelection();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelX && state != null) {
            int delta = scrollY > 0 ? -1 : 1;
            if (tab == Tab.CLAIMS) {
                claimScroll += delta;
            } else if (tab == Tab.MEMBERS) {
                memberScroll += delta;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Claims the free cells or releases the editable cells in the dragged rectangle. */
    private void applySelection() {
        int stateSize = 2 * state.radius() + 1;
        int startIdx = state.cells()[dragStartRow * stateSize + dragStartCol];
        boolean claim;
        if (startIdx < 0) {
            claim = true;
        } else {
            byte rel = state.gridClaims().get(startIdx).relation();
            if (rel != StatePayload.OWN && rel != StatePayload.ADMIN_EDITABLE) {
                return;
            }
            claim = false;
        }
        List<Long> chunks = new ArrayList<>();
        for (int row = Math.min(dragStartRow, dragEndRow); row <= Math.max(dragStartRow, dragEndRow); row++) {
            for (int col = Math.min(dragStartCol, dragEndCol); col <= Math.max(dragStartCol, dragEndCol); col++) {
                int idx = state.cells()[row * stateSize + col];
                boolean include = claim ? idx < 0
                        : idx >= 0 && (state.gridClaims().get(idx).relation() == StatePayload.OWN
                                || state.gridClaims().get(idx).relation() == StatePayload.ADMIN_EDITABLE);
                if (include) {
                    chunks.add(ChunkCoord.pack(state.centerX() - state.radius() + col, state.centerZ() - state.radius() + row));
                }
            }
        }
        if (chunks.isEmpty()) {
            return;
        }
        Optional<UUID> target = Optional.empty();
        StatePayload.ClaimDetail d = selectedDetail();
        if (claim && d != null && d.dimension().equals(state.dimension())) {
            target = Optional.of(d.id());
        }
        send(new ClaimChunksPayload(claim, target, chunks));
    }
}
