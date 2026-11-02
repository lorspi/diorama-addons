package io.github.dioramaaddons.webmap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.dioramaaddons.webmap.config.WebMapConfig;
import io.github.dioramaaddons.webmap.render.MapRenderer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /webmap status | render [dimension] | clearcache | restart | reload} for operators. */
final class WebMapCommand {
    private WebMapCommand() {}

    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("webmap")
                .requires(src -> src.hasPermission(2))
                .executes(WebMapCommand::status)
                .then(Commands.literal("status").executes(WebMapCommand::status))
                .then(Commands.literal("render")
                        .executes(ctx -> render(ctx, null))
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(ctx -> render(ctx, DimensionArgument.getDimension(ctx, "dimension")
                                        .dimension().location().toString()))))
                .then(Commands.literal("clearcache").executes(WebMapCommand::clearCache))
                .then(Commands.literal("restart").executes(WebMapCommand::restart))
                .then(Commands.literal("reload").executes(WebMapCommand::reload)));
    }

    private static WebMapService service(CommandContext<CommandSourceStack> ctx) {
        WebMapService s = WebMapService.get();
        if (s == null) {
            ctx.getSource().sendFailure(ServerLang.tr(ctx.getSource(), "dioaddons_webmap.cmd.not_running"));
        }
        return s;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        WebMapService s = service(ctx);
        if (s == null) {
            return 0;
        }
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> s.web().running()
                ? ServerLang.tr(src, "dioaddons_webmap.cmd.http_on", WebMapConfig.BIND.get(), WebMapConfig.PORT.get())
                : ServerLang.tr(src, "dioaddons_webmap.cmd.http_off"), false);
        MapRenderer r = s.renderer();
        if (r == null) {
            src.sendSuccess(() -> ServerLang.tr(src, "dioaddons_webmap.cmd.render_off"), false);
        } else {
            String current = r.current();
            src.sendSuccess(() -> ServerLang.tr(src, "dioaddons_webmap.cmd.render_status", r.queuedRegions(),
                    r.queuedParents(), r.chunksRendered(), r.tilesWritten(), current == null ? "-" : current), false);
        }
        src.sendSuccess(() -> ServerLang.tr(src, "dioaddons_webmap.cmd.layers",
                String.join(", ", s.dimensions()), yesNo(src, s.claimsAvailable()), yesNo(src, s.playersShown()),
                yesNo(src, s.onlineListShown()), yesNo(src, s.chatShown())), false);
        return 1;
    }

    private static net.minecraft.network.chat.Component yesNo(CommandSourceStack src, boolean value) {
        return ServerLang.tr(src, value ? "dioaddons_webmap.cmd.yes" : "dioaddons_webmap.cmd.no");
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        WebMapService s = service(ctx);
        if (s == null) {
            return 0;
        }
        CommandSourceStack src = ctx.getSource();
        String error = io.github.dioramaaddons.webmap.config.ConfigReload.reload();
        if (error != null) {
            src.sendFailure(ServerLang.tr(src, "dioaddons_webmap.cmd.reload_failed", error));
            return 0;
        }
        s.applyConfig();
        src.sendSuccess(() -> ServerLang.tr(src, "dioaddons_webmap.cmd.reloaded"), true);
        return 1;
    }

    private static int restart(CommandContext<CommandSourceStack> ctx) {
        WebMapService s = service(ctx);
        if (s == null) {
            return 0;
        }
        CommandSourceStack src = ctx.getSource();
        boolean running = s.startHttp(true);
        if (running) {
            src.sendSuccess(() -> ServerLang.tr(src, "dioaddons_webmap.cmd.restarted", WebMapConfig.BIND.get(),
                    WebMapConfig.PORT.get()), true);
            return 1;
        }
        src.sendFailure(ServerLang.tr(src, "dioaddons_webmap.cmd.http_off"));
        return 0;
    }

    private static int render(CommandContext<CommandSourceStack> ctx, String dim) throws CommandSyntaxException {
        WebMapService s = service(ctx);
        if (s == null) {
            return 0;
        }
        if (s.renderer() == null) {
            ctx.getSource().sendFailure(ServerLang.tr(ctx.getSource(), "dioaddons_webmap.cmd.render_off"));
            return 0;
        }
        s.renderer().scan(dim, true);
        ctx.getSource().sendSuccess(() -> ServerLang.tr(ctx.getSource(), "dioaddons_webmap.cmd.render_started",
                dim == null ? "*" : dim), true);
        return 1;
    }

    private static int clearCache(CommandContext<CommandSourceStack> ctx) {
        WebMapService s = service(ctx);
        if (s == null) {
            return 0;
        }
        if (s.renderer() == null) {
            ctx.getSource().sendFailure(ServerLang.tr(ctx.getSource(), "dioaddons_webmap.cmd.render_off"));
            return 0;
        }
        s.renderer().clearCache();
        ctx.getSource().sendSuccess(() -> ServerLang.tr(ctx.getSource(), "dioaddons_webmap.cmd.cleared"), true);
        return 1;
    }
}
