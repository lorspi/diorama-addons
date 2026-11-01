package io.github.dioramaaddons.clientmap.server;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.dioramaaddons.clientmap.core.GroupRegistry;
import io.github.dioramaaddons.clientmap.core.MapGroup;
import io.github.dioramaaddons.clientmap.core.SharedWaypoint;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /mapgroup}, {@code /sharedwaypoint} and {@code /maplocation}. Work without any client mod. */
public final class ClientMapCommands {
    private ClientMapCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("mapgroup")
                .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> group(ctx, (s, p) -> s.groups().create(p.getUUID(), p.getGameProfile().getName(),
                                StringArgumentType.getString(ctx, "name").trim()), "dioaddons_clientmap.group.created"))))
                .then(Commands.literal("invite").then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(ClientMapCommands::invite)))
                .then(Commands.literal("join").then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(service().groups().all().stream().map(MapGroup::name), b))
                        .executes(ClientMapCommands::join)))
                .then(Commands.literal("leave").executes(ctx -> group(ctx, (s, p) -> s.groups().leave(p.getUUID()),
                        "dioaddons_clientmap.group.left")))
                .then(Commands.literal("kick").then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(ClientMapCommands::kick)))
                .then(Commands.literal("info").executes(ClientMapCommands::info))
                .executes(ClientMapCommands::info));

        d.register(Commands.literal("sharedwaypoint")
                .then(Commands.literal("add").then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ClientMapCommands::addWaypoint)))
                .then(Commands.literal("list").executes(ClientMapCommands::listWaypoints))
                .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests(ClientMapCommands::suggestWaypoints)
                        .executes(ClientMapCommands::removeWaypoint)))
                .then(Commands.literal("rename")
                        .then(Commands.argument("old", StringArgumentType.string()).suggests(ClientMapCommands::suggestWaypoints)
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ClientMapCommands::renameWaypoint))))
                .executes(ClientMapCommands::listWaypoints));

        d.register(Commands.literal("maplocation")
                .then(Commands.literal("show").executes(ctx -> visibility(ctx, true)))
                .then(Commands.literal("hide").executes(ctx -> visibility(ctx, false)))
                .executes(ClientMapCommands::locationStatus));
    }

    private static ClientMapServer service() {
        ClientMapServer s = ClientMapServer.get();
        if (s == null) {
            throw new IllegalStateException("clientmap is not running");
        }
        return s;
    }

    private interface GroupAction {
        GroupRegistry.Result run(ClientMapServer s, ServerPlayer player);
    }

    private static int group(CommandContext<CommandSourceStack> ctx, GroupAction action, String successKey)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ClientMapServer s = service();
        UUID oldGroup = s.groups().groupIdOf(player.getUUID());
        List<UUID> before = oldGroup == null ? List.of(player.getUUID())
                : List.copyOf(s.groups().groupOf(player.getUUID()).get().members().keySet());
        GroupRegistry.Result r = action.run(s, player);
        if (r != GroupRegistry.Result.SUCCESS) {
            ctx.getSource().sendFailure(ServerLang.tr(ctx.getSource(), r.translationKey()));
            return 0;
        }
        UUID newGroup = s.groups().groupIdOf(player.getUUID());
        if (newGroup != null && oldGroup == null) {
            s.mergeIntoGroup(player.getUUID(), newGroup);
        }
        s.data().setDirty();
        s.refreshPlayers(before);
        if (newGroup != null) {
            s.refreshPlayers(s.groups().groupOf(player.getUUID()).get().members().keySet());
        }
        String name = s.groups().groupOf(player.getUUID()).map(MapGroup::name).orElse("");
        ctx.getSource().sendSuccess(() -> ServerLang.tr(ctx.getSource(), successKey, name), false);
        return 1;
    }

    private static GameProfile profile(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        return profiles.iterator().next();
    }

    private static int invite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        GameProfile target = profile(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        GroupRegistry.Result r = service().groups().invite(player.getUUID(), target.getId());
        if (r != GroupRegistry.Result.SUCCESS) {
            ctx.getSource().sendFailure(ServerLang.tr(ctx.getSource(), r.translationKey()));
            return 0;
        }
        service().data().setDirty();
        String group = service().groups().groupOf(player.getUUID()).map(MapGroup::name).orElse("");
        ctx.getSource().sendSuccess(() -> ServerLang.tr(ctx.getSource(), "dioaddons_clientmap.group.invited", target.getName()), false);
        ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayer(target.getId());
        if (online != null) {
            online.sendSystemMessage(ServerLang.tr(online, "dioaddons_clientmap.group.invitation", player.getGameProfile().getName(), group));
        }
        return 1;
    }

    private static int join(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name").trim();
        return group(ctx, (s, p) -> s.groups().join(p.getUUID(), p.getGameProfile().getName(), name),
                "dioaddons_clientmap.group.joined");
    }

    private static int kick(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        GameProfile target = profile(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ClientMapServer s = service();
        GroupRegistry.Result r = s.groups().kick(player.getUUID(), target.getId());
        if (r != GroupRegistry.Result.SUCCESS) {
            ctx.getSource().sendFailure(ServerLang.tr(ctx.getSource(), r.translationKey()));
            return 0;
        }
        s.data().setDirty();
        s.refreshPlayers(List.of(target.getId()));
        s.refreshPlayers(s.groups().groupOf(player.getUUID()).get().members().keySet());
        ctx.getSource().sendSuccess(() -> ServerLang.tr(ctx.getSource(), "dioaddons_clientmap.group.kicked", target.getName()), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ClientMapServer s = service();
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> ServerLang.tr(src, "dioaddons_clientmap.info.modes", s.exploredMode().name(),
                s.locationsMode().name()), false);
        s.groups().groupOf(player.getUUID()).ifPresentOrElse(
                g -> src.sendSuccess(() -> ServerLang.tr(src, "dioaddons_clientmap.info.group", g.name(),
                        String.join(", ", g.members().values()), g.members().getOrDefault(g.owner(), "?")), false),
                () -> src.sendSuccess(() -> ServerLang.tr(src, "dioaddons_clientmap.info.no_group"), false));
        return 1;
    }

    private static int addWaypoint(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        ClientMapServer.WaypointResult r = service().addWaypoint(player, name, ClientMapServer.dim(player.serverLevel()),
                player.getBlockX(), player.getBlockY(), player.getBlockZ(), 0xFF2F80ED);
        return waypointResult(ctx, r, "dioaddons_clientmap.waypoint.added", name.trim());
    }

    private static int waypointResult(CommandContext<CommandSourceStack> ctx, ClientMapServer.WaypointResult r, String key,
            Object... args) {
        if (r != ClientMapServer.WaypointResult.SUCCESS) {
            ctx.getSource().sendFailure(ServerLang.tr(ctx.getSource(), "dioaddons_clientmap.waypoint." + r.name().toLowerCase()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> ServerLang.tr(ctx.getSource(), key, args), false);
        return 1;
    }

    private static int listWaypoints(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<SharedWaypoint> list = service().visibleWaypoints(player.getUUID());
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> ServerLang.tr(src, "dioaddons_clientmap.waypoint.list_header", list.size()), false);
        for (SharedWaypoint w : list) {
            src.sendSuccess(() -> ServerLang.tr(src, "dioaddons_clientmap.waypoint.list_entry", w.name(), w.x(), w.y(), w.z(),
                    w.dimension(), w.ownerName()), false);
        }
        return list.size();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestWaypoints(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        ServerPlayer p = ctx.getSource().getPlayer();
        ClientMapServer s = ClientMapServer.get();
        if (p == null || s == null) {
            return b.buildFuture();
        }
        return SharedSuggestionProvider.suggest(s.visibleWaypoints(p.getUUID()).stream()
                .map(w -> w.name().contains(" ") ? "\"" + w.name() + "\"" : w.name()), b);
    }

    private static int removeWaypoint(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name").replace("\"", "").trim();
        SharedWaypoint w = service().find(player, name);
        if (w == null) {
            return waypointResult(ctx, ClientMapServer.WaypointResult.NOT_FOUND, "");
        }
        return waypointResult(ctx, service().deleteWaypoint(player, w.id()), "dioaddons_clientmap.waypoint.removed", w.name());
    }

    private static int renameWaypoint(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        SharedWaypoint w = service().find(player, StringArgumentType.getString(ctx, "old"));
        if (w == null) {
            return waypointResult(ctx, ClientMapServer.WaypointResult.NOT_FOUND, "");
        }
        String name = StringArgumentType.getString(ctx, "name");
        return waypointResult(ctx, service().renameWaypoint(player, w.id(), name), "dioaddons_clientmap.waypoint.renamed",
                w.name(), name.trim());
    }

    private static int visibility(CommandContext<CommandSourceStack> ctx, boolean visible) throws CommandSyntaxException {
        service().setVisible(ctx.getSource().getPlayerOrException(), visible);
        ctx.getSource().sendSuccess(() -> ServerLang.tr(ctx.getSource(),
                visible ? "dioaddons_clientmap.location.shown" : "dioaddons_clientmap.location.hidden"), false);
        return 1;
    }

    private static int locationStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        boolean visible = service().isVisible(ctx.getSource().getPlayerOrException().getUUID());
        ctx.getSource().sendSuccess(() -> ServerLang.tr(ctx.getSource(),
                visible ? "dioaddons_clientmap.location.shown" : "dioaddons_clientmap.location.hidden"), false);
        return 1;
    }
}
