package io.github.dioramaaddons.claims.server;

import io.github.dioramaaddons.claims.config.ClaimsConfig;
import io.github.dioramaaddons.claims.core.Claim;
import io.github.dioramaaddons.claims.core.ClaimMember;
import io.github.dioramaaddons.claims.core.ClaimSettings;
import io.github.dioramaaddons.claims.core.TextFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Welcome and farewell notifications for whoever enters or leaves a claim, and the visitor alarm that tells the
 * owner and/or the members when someone who is neither comes in. Everything is sent with vanilla packets, so players
 * without the mod see it too. Server thread only.
 */
final class ClaimGreetings {
    private static final int CHECK_TICKS = 10;

    private final ClaimsService service;
    private final MinecraftServer server;
    /** The claim each online player is in; no entry = outside any claim. */
    private final Map<UUID, UUID> inside = new HashMap<>();
    /** Last alarm per visitor and claim ({@code visitor|claim}), in milliseconds. */
    private final Map<String, Long> alarms = new HashMap<>();
    private int ticks;

    ClaimGreetings(ClaimsService service, MinecraftServer server) {
        this.service = service;
        this.server = server;
    }

    void onLogout(ServerPlayer p) {
        inside.remove(p.getUUID());
    }

    void tick() {
        if (++ticks % CHECK_TICKS != 0) {
            return;
        }
        if (!ClaimsConfig.SERVER.greetingsEnabled.get()) {
            inside.clear();
            return;
        }
        long cooldown = ClaimsConfig.SERVER.alarmCooldownSeconds.get() * 1000L;
        if (ticks % 1200 == 0) {
            long now = System.currentTimeMillis();
            alarms.values().removeIf(t -> now - t > cooldown);
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Claim now = p.isSpectator() ? null : service.claimAt(p.level(), p.blockPosition());
            UUID nowId = now == null ? null : now.id();
            UUID before = inside.get(p.getUUID());
            if (Objects.equals(before, nowId)) {
                continue;
            }
            if (nowId == null) {
                inside.remove(p.getUUID());
            } else {
                inside.put(p.getUUID(), nowId);
            }
            if (before != null) {
                service.store().claim(before).ifPresent(c -> left(p, c));
            }
            if (now != null) {
                entered(p, now, cooldown);
            }
        }
    }

    private void left(ServerPlayer p, Claim c) {
        ClaimSettings s = c.settings();
        if (s.hasFarewell()) {
            greet(p, c, s.farewellTitle(), s.farewell());
        }
    }

    private void entered(ServerPlayer p, Claim c, long cooldown) {
        ClaimSettings s = c.settings();
        if (s.hasWelcome()) {
            greet(p, c, s.welcomeTitle(), s.welcome());
        }
        boolean visitor = !c.isOwner(p.getUUID()) && !c.isMember(p.getUUID());
        if (!visitor || !s.alarms()) {
            return;
        }
        long now = System.currentTimeMillis();
        String key = p.getUUID() + "|" + c.id();
        Long last = alarms.get(key);
        if (last != null && now - last < cooldown) {
            return;
        }
        alarms.put(key, now);
        if (s.alarmOwner()) {
            alarm(server.getPlayerList().getPlayer(c.owner()), p, c);
        }
        if (s.alarmMembers()) {
            for (ClaimMember m : c.members()) {
                alarm(server.getPlayerList().getPlayer(m.id()), p, c);
            }
        }
    }

    private void greet(ServerPlayer p, Claim c, String title, String text) {
        Map<String, String> values = Map.of("player", p.getGameProfile().getName(), "claim", c.name(),
                "owner", c.ownerName());
        // Without a title the notification is titled with the claim's name.
        Component head = title.isEmpty() ? Component.literal(c.name()).withStyle(ChatFormatting.GOLD)
                : format(TextFormat.fill(title, values));
        Component body = format(TextFormat.fill(text, values));
        switch (ClaimsConfig.SERVER.greetingDisplay.get()) {
            case TITLE -> {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
                p.connection.send(new ClientboundSetSubtitleTextPacket(body));
                p.connection.send(new ClientboundSetTitleTextPacket(head));
            }
            case ACTION_BAR -> p.displayClientMessage(Component.empty().append(head).append(" ").append(body), true);
            case CHAT -> p.sendSystemMessage(Component.empty().append(head).append(Component.literal(" · ")
                    .withStyle(ChatFormatting.DARK_GRAY)).append(body));
        }
    }

    private static void alarm(ServerPlayer to, ServerPlayer visitor, Claim c) {
        if (to == null || to == visitor) {
            return;
        }
        to.sendSystemMessage(ServerLang.tr(to, "dioaddons_claims.alarm.text", visitor.getGameProfile().getName(), c.name())
                .withStyle(ChatFormatting.GOLD));
        to.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8f, 0.8f);
    }

    /** A text with formatting codes ({@code &a}, {@code &#RRGGBB}, {@code &l} …) as a chat component. */
    static MutableComponent format(String text) {
        MutableComponent out = Component.empty();
        for (TextFormat.Run r : TextFormat.runs(text)) {
            Style st = Style.EMPTY;
            if (r.color() >= 0) {
                st = st.withColor(TextColor.fromRgb(r.color()));
            }
            st = st.withBold((r.flags() & TextFormat.BOLD) != 0 ? Boolean.TRUE : null)
                    .withItalic((r.flags() & TextFormat.ITALIC) != 0 ? Boolean.TRUE : null)
                    .withUnderlined((r.flags() & TextFormat.UNDERLINE) != 0 ? Boolean.TRUE : null)
                    .withStrikethrough((r.flags() & TextFormat.STRIKE) != 0 ? Boolean.TRUE : null);
            out.append(Component.literal(r.text()).withStyle(st));
        }
        return out;
    }
}
