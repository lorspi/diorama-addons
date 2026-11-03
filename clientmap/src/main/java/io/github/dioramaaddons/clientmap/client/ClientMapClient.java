package io.github.dioramaaddons.clientmap.client;

import com.mojang.brigadier.Command;
import com.mojang.logging.LogUtils;
import io.github.dioramaaddons.clientmap.compat.diorama.DioramaBridge;
import io.github.dioramaaddons.clientmap.compat.diorama.DioramaCompat;
import io.github.dioramaaddons.clientmap.core.ShareMode;
import io.github.dioramaaddons.clientmap.network.ClientMapNetwork;
import io.github.dioramaaddons.clientmap.network.CmPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Client side. Does nothing unless a supported Diorama is installed: then it says hello to the server
 * and passes what it receives to {@link DioramaBridge}. Client thread only.
 */
public final class ClientMapClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean active;
    private static @Nullable CmPayloads.Session session;
    /** Pools already asked for in {@link #requestedDimension}. */
    private static final java.util.Set<String> requested = new java.util.HashSet<>();
    private static @Nullable String requestedDimension;
    private static int chunksReceived;

    private ClientMapClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        // Mods list → Config opens the map settings (also without Diorama: the client settings are still kept).
        container.registerExtensionPoint(IConfigScreenFactory.class, (c, parent) -> new MapSettingsScreen(parent));
        active = DioramaCompat.active();
        if (!active) {
            if (DioramaCompat.installed()) {
                LOGGER.warn("Diorama {} is not a version the map module supports ({}); the map integration is off",
                        DioramaCompat.installedVersion(), DioramaCompat.SUPPORTED);
            } else {
                LOGGER.info("Diorama is not installed; the map module has nothing to do on this client");
            }
            return;
        }
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> onJoin());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> onLeave());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> e.getDispatcher().register(Commands.literal("clientmap")
                .then(Commands.literal("resync").executes(ctx -> {
                    SyncProgress.reset(serverKey());
                    requested.clear();
                    ctx.getSource().sendSystemMessage(Component.translatable("dioaddons_clientmap.client.resync"));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("settings").executes(ctx -> {
                    // The chat screen closes after the command runs; open on the next tick.
                    Minecraft mc = Minecraft.getInstance();
                    mc.tell(() -> mc.setScreen(new MapSettingsScreen(null)));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ctx.getSource().sendSystemMessage(Component.translatable("dioaddons_clientmap.client.status",
                            session == null ? "-" : session.exploredMode().name() + " " + session.exploredScopes().size(),
                            session == null ? "-" : session.locationsMode().name(),
                            chunksReceived));
                    return Command.SINGLE_SUCCESS;
                }))));
        LOGGER.info("Diorama {} found: map integration on", DioramaCompat.installedVersion());
    }

    public static @Nullable CmPayloads.Session session() {
        return session;
    }

    private static void onJoin() {
        DioramaBridge.clear();
        session = null;
        requestedDimension = null;
        requested.clear();
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null && connection.hasChannel(CmPayloads.Hello.TYPE)) {
            PacketDistributor.sendToServer(new CmPayloads.Hello(ClientMapNetwork.PROTOCOL));
        }
    }

    private static void onLeave() {
        DioramaBridge.clear();
        session = null;
        requestedDimension = null;
        requested.clear();
        SyncProgress.save();
    }

    static String serverKey() {
        Minecraft mc = Minecraft.getInstance();
        ServerData data = mc.getCurrentServer();
        if (data != null) {
            return data.ip;
        }
        var sp = mc.getSingleplayerServer();
        return sp == null ? "unknown" : "local:" + sp.getWorldData().getLevelName();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (session == null || mc.level == null) {
            return;
        }
        String dim = mc.level.dimension().location().toString();
        if (!dim.equals(requestedDimension)) {
            requestedDimension = dim;
            requested.clear();
        }
        if (session.exploredMode() != ShareMode.DISABLED) {
            for (String scope : session.exploredScopes()) {
                if (requested.add(scope)) {
                    int from = SyncProgress.get(SyncProgress.key(serverKey(), dim, scope));
                    PacketDistributor.sendToServer(new CmPayloads.ExploredRequest(dim, scope, from));
                }
            }
        }
        if (mc.level.getGameTime() % 200 == 0) {
            SyncProgress.save();
        }
    }

    // ---------------------------------------------------------------- payloads (client thread)

    public static void onSession(CmPayloads.Session s) {
        if (!active) {
            return;
        }
        session = s;
        DioramaBridge.setClaimsEnabled(s.claims());
        // Pools no longer ours are asked for again if they come back.
        requested.retainAll(s.exploredScopes());
    }

    public static void onClaims(CmPayloads.Claims p) {
        if (active) {
            DioramaBridge.onClaims(p);
        }
    }

    public static void onWaypoints(CmPayloads.Waypoints p) {
        if (active) {
            DioramaBridge.onWaypoints(p);
        }
    }

    public static void onPlayers(CmPayloads.Players p) {
        if (active) {
            DioramaBridge.onPlayers(p);
        }
    }

    public static void onExplored(CmPayloads.ExploredChunks p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!active || level == null || session == null || !level.dimension().location().toString().equals(p.dimension())
                || !session.exploredScopes().contains(p.scope())) {
            return;
        }
        for (CmPayloads.ChunkEntry entry : p.chunks()) {
            try {
                LevelChunk chunk = ChunkBuilder.build(level, entry);
                if (chunk != null && DioramaBridge.feed(level, chunk)) {
                    chunksReceived++;
                }
            } catch (RuntimeException e) {
                LOGGER.debug("Could not use explored chunk {}, {}", entry.x(), entry.z(), e);
            }
        }
        SyncProgress.set(SyncProgress.key(serverKey(), p.dimension(), p.scope()), p.next());
        // Ask for the rest: the server keeps streaming as long as the request stands.
    }
}
