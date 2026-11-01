package io.github.dioramaaddons.claims.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.dioramaaddons.claims.config.ClaimsConfig;
import io.github.dioramaaddons.claims.core.Claim;
import io.github.dioramaaddons.claims.core.ClaimMember;
import io.github.dioramaaddons.claims.core.ClaimResult;
import io.github.dioramaaddons.claims.core.ClaimStore;
import io.github.dioramaaddons.claims.core.Permission;
import io.github.dioramaaddons.claims.core.PermissionResolver;
import io.github.dioramaaddons.claims.core.PermissionTemplate;
import io.github.dioramaaddons.claims.network.OpenScreenPayload;
import io.github.dioramaaddons.claims.server.ClaimsService;
import io.github.dioramaaddons.claims.server.ServerLang;
import io.github.dioramaaddons.claimsapi.ChunkCoord;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Every action is available as a command so players without the client mod can use the mod:
 * {@code /claim}, {@code /unclaim} and {@code /claims ...}.
 */
public final class ClaimsCommands {
    private ClaimsCommands() {}

    private static final SuggestionProvider<CommandSourceStack> PERMISSIONS = (ctx, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Permission.values()).map(Permission::id), b);
    private static final SuggestionProvider<CommandSourceStack> MEMBER_PERMISSIONS = (ctx, b) ->
            SharedSuggestionProvider.suggest(Permission.playerPermissions().stream().map(Permission::id), b);
    private static final SuggestionProvider<CommandSourceStack> TEMPLATES = (ctx, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(PermissionTemplate.values()).map(PermissionTemplate::id), b);

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("claim")
                .executes(ctx -> claim(ctx, null))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> claim(ctx, StringArgumentType.getString(ctx, "name")))));
        d.register(Commands.literal("unclaim").executes(ClaimsCommands::unclaim));

        d.register(Commands.literal("claims")
                .executes(ClaimsCommands::help)
                .then(Commands.literal("help").executes(ClaimsCommands::help))
                .then(Commands.literal("menu").executes(ClaimsCommands::menu))
                .then(Commands.literal("list").executes(ctx -> list(ctx, ctx.getSource().getPlayerOrException().getUUID())))
                .then(Commands.literal("info").executes(ClaimsCommands::info))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ClaimsCommands::rename)))
                .then(Commands.literal("delete").executes(ClaimsCommands::delete))
                .then(Commands.literal("trust")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> trust(ctx, PermissionTemplate.MEMBER))
                                .then(Commands.argument("template", StringArgumentType.word()).suggests(TEMPLATES)
                                        .executes(ctx -> trust(ctx, template(ctx))))))
                .then(Commands.literal("untrust")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ClaimsCommands::untrust)))
                .then(Commands.literal("permission")
                        .then(Commands.literal("public")
                                .then(Commands.argument("permission", StringArgumentType.word()).suggests(PERMISSIONS)
                                        .then(Commands.argument("allow", BoolArgumentType.bool())
                                                .executes(ClaimsCommands::setPublic))))
                        .then(Commands.literal("member")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("permission", StringArgumentType.word())
                                                .suggests(MEMBER_PERMISSIONS)
                                                .then(Commands.argument("allow", BoolArgumentType.bool())
                                                        .executes(ClaimsCommands::setMemberPermission)))))
                        .then(Commands.literal("template")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("template", StringArgumentType.word()).suggests(TEMPLATES)
                                                .executes(ctx -> trust(ctx, template(ctx)))))))
                .then(Commands.literal("admin")
                        .requires(src -> src.hasPermission(ClaimsConfig.SERVER.adminPermissionLevel.get()))
                        .then(Commands.literal("bypass").executes(ClaimsCommands::bypass))
                        .then(Commands.literal("list")
                                .executes(ClaimsCommands::adminListAll)
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(ClaimsCommands::adminListPlayer)))
                        .then(Commands.literal("delete").executes(ClaimsCommands::adminDelete))
                        .then(Commands.literal("unclaim").executes(ClaimsCommands::adminUnclaim))));
    }

    // ---------------------------------------------------------------- helpers

    private static ClaimsService service() throws CommandSyntaxException {
        ClaimsService s = ClaimsService.get();
        if (s == null) {
            throw new IllegalStateException("Claims service is not running");
        }
        return s;
    }

    private static int fail(CommandSourceStack src, String key, Object... args) {
        src.sendFailure(ServerLang.tr(src, key, args));
        return 0;
    }

    private static int ok(CommandSourceStack src, String key, Object... args) {
        MutableComponent msg = ServerLang.tr(src, key, args);
        src.sendSuccess(() -> msg, false);
        return 1;
    }

    private static int result(CommandSourceStack src, ClaimResult result, String successKey, Object... args) {
        return result.ok() ? ok(src, successKey, args) : fail(src, result.translationKey());
    }

    private static PermissionTemplate template(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "template");
        return PermissionTemplate.byId(id).orElseThrow(() -> new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                ServerLang.tr(ctx.getSource(), "dioaddons_claims.error.unknown_template", id)).create());
    }

    private static Permission permission(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "permission");
        return Permission.byId(id).orElseThrow(() -> new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                ServerLang.tr(ctx.getSource(), "dioaddons_claims.error.unknown_permission", id)).create());
    }

    private static GameProfile singleProfile(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        if (profiles.size() != 1) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    ServerLang.tr(ctx.getSource(), "dioaddons_claims.error.one_player")).create();
        }
        return profiles.iterator().next();
    }

    private static Claim claimHere(ServerPlayer player) throws CommandSyntaxException {
        Claim claim = service().claimAt(player.level(), player.blockPosition());
        return claim;
    }

    /** Claim at the player's position that they may edit, or null after sending an error. */
    private static Claim manageableHere(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Claim claim = claimHere(player);
        if (claim == null) {
            fail(ctx.getSource(), "dioaddons_claims.error.not_in_claim");
            return null;
        }
        if (!ClaimStore.canManage(service().actor(player), claim)) {
            fail(ctx.getSource(), ClaimResult.NOT_OWNER.translationKey());
            return null;
        }
        return claim;
    }

    private static Component permissionList(CommandSourceStack src, EnumSet<Permission> perms) {
        if (perms.isEmpty()) {
            return ServerLang.tr(src, "dioaddons_claims.info.none");
        }
        MutableComponent out = Component.empty();
        boolean first = true;
        for (Permission p : perms) {
            if (!first) {
                out.append(", ");
            }
            out.append(ServerLang.tr(src, p.translationKey()));
            first = false;
        }
        return out;
    }

    // ---------------------------------------------------------------- player commands

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        for (String line : List.of("claim", "unclaim", "menu", "list", "info", "rename", "delete", "trust", "untrust",
                "permission_public", "permission_member", "permission_template")) {
            src.sendSuccess(() -> ServerLang.tr(src, "dioaddons_claims.help." + line), false);
        }
        return 1;
    }

    private static int claim(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ClaimsService s = service();
        ChunkPos chunk = player.chunkPosition();
        if (name != null && !ClaimStore.isValidName(name)) {
            return fail(ctx.getSource(), ClaimResult.INVALID_NAME.translationKey());
        }
        ClaimStore.Outcome o = s.store().claimChunk(s.actor(player), ClaimsService.dimensionId(player.level()),
                chunk.toLong(), null, name, ClaimsConfig.limits(), s.spawnChunk(player.serverLevel()));
        if (!o.result().ok()) {
            return fail(ctx.getSource(), o.result().translationKey());
        }
        return ok(ctx.getSource(), "dioaddons_claims.cmd.claimed", chunk.x, chunk.z, o.claim().name(),
                s.store().chunkCount(player.getUUID()), ClaimsConfig.limits().maxChunksPerPlayer());
    }

    private static int unclaim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ClaimsService s = service();
        ChunkPos chunk = player.chunkPosition();
        ClaimStore.Outcome o = s.store().unclaimChunk(s.actor(player), ClaimsService.dimensionId(player.level()),
                chunk.toLong(), ClaimsConfig.limits());
        return result(ctx.getSource(), o.result(), "dioaddons_claims.cmd.unclaimed", chunk.x, chunk.z);
    }

    private static int menu(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!player.connection.hasChannel(OpenScreenPayload.TYPE)) {
            return fail(ctx.getSource(), "dioaddons_claims.error.no_client_mod");
        }
        PacketDistributor.sendToPlayer(player, OpenScreenPayload.INSTANCE);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, UUID owner) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ClaimsService s = service();
        List<Claim> claims = s.store().claimsOf(owner);
        src.sendSuccess(() -> ServerLang.tr(src, "dioaddons_claims.cmd.list_header", claims.size(),
                ClaimsConfig.limits().maxClaimsPerPlayer(), s.store().chunkCount(owner),
                ClaimsConfig.limits().maxChunksPerPlayer()), false);
        for (Claim c : claims) {
            src.sendSuccess(() -> describe(src, c), false);
        }
        return claims.size();
    }

    private static Component describe(CommandSourceStack src, Claim c) {
        long first = c.chunks().iterator().next();
        return ServerLang.tr(src, "dioaddons_claims.cmd.list_entry", c.name(), c.ownerName(), c.dimension(),
                c.chunks().size(), ChunkCoord.unpackX(first) * 16 + 8, ChunkCoord.unpackZ(first) * 16 + 8)
                .withStyle(ChatFormatting.GRAY);
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        Claim claim = claimHere(player);
        if (claim == null) {
            return ok(src, "dioaddons_claims.info.unclaimed");
        }
        src.sendSuccess(() -> ServerLang.tr(src, "dioaddons_claims.info.header", claim.name(), claim.ownerName(),
                claim.chunks().size()), false);
        src.sendSuccess(() -> ServerLang.tr(src, "dioaddons_claims.info.yours",
                permissionList(src, PermissionResolver.effective(claim, player.getUUID()))), false);
        EnumSet<Permission> flags = EnumSet.noneOf(Permission.class);
        for (Permission p : claim.publicPermissions()) {
            if (p.scope() == Permission.Scope.CLAIM) {
                flags.add(p);
            }
        }
        src.sendSuccess(() -> ServerLang.tr(src, "dioaddons_claims.info.flags", permissionList(src, flags)), false);
        if (ClaimStore.canManage(service().actor(player), claim)) {
            EnumSet<Permission> pub = claim.publicPermissions();
            pub.removeAll(flags);
            src.sendSuccess(() -> ServerLang.tr(src, "dioaddons_claims.info.public", permissionList(src, pub)), false);
            for (ClaimMember m : claim.members()) {
                src.sendSuccess(() -> ServerLang.tr(src, "dioaddons_claims.info.member", m.name(),
                        permissionList(src, m.permissions())), false);
            }
        }
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Claim claim = manageableHere(ctx);
        if (claim == null) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ClaimResult r = service().store().rename(service().actor(player), claim.id(), name).result();
        return result(ctx.getSource(), r, "dioaddons_claims.cmd.renamed", name.trim());
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Claim claim = manageableHere(ctx);
        if (claim == null) {
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ClaimResult r = service().store().delete(service().actor(player), claim.id()).result();
        return result(ctx.getSource(), r, "dioaddons_claims.cmd.deleted", claim.name());
    }

    private static int trust(CommandContext<CommandSourceStack> ctx, PermissionTemplate template)
            throws CommandSyntaxException {
        Claim claim = manageableHere(ctx);
        if (claim == null) {
            return 0;
        }
        GameProfile profile = singleProfile(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ClaimResult r = service().store().setMember(service().actor(player), claim.id(), profile.getId(),
                profile.getName(), template.permissions(), ClaimsConfig.limits()).result();
        return result(ctx.getSource(), r, "dioaddons_claims.cmd.trusted", profile.getName(), claim.name(),
                ServerLang.tr(ctx.getSource(), template.translationKey()));
    }

    private static int untrust(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Claim claim = manageableHere(ctx);
        if (claim == null) {
            return 0;
        }
        GameProfile profile = singleProfile(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ClaimResult r = service().store().removeMember(service().actor(player), claim.id(), profile.getId()).result();
        return result(ctx.getSource(), r, "dioaddons_claims.cmd.untrusted", profile.getName(), claim.name());
    }

    private static int setPublic(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Claim claim = manageableHere(ctx);
        if (claim == null) {
            return 0;
        }
        Permission perm = permission(ctx);
        boolean allow = BoolArgumentType.getBool(ctx, "allow");
        EnumSet<Permission> perms = claim.publicPermissions();
        if (allow) {
            perms.add(perm);
        } else {
            perms.remove(perm);
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ClaimResult r = service().store().setPublicPermissions(service().actor(player), claim.id(), perms).result();
        return result(ctx.getSource(), r, allow ? "dioaddons_claims.cmd.public_allowed" : "dioaddons_claims.cmd.public_denied",
                ServerLang.tr(ctx.getSource(), perm.translationKey()), claim.name());
    }

    private static int setMemberPermission(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Claim claim = manageableHere(ctx);
        if (claim == null) {
            return 0;
        }
        GameProfile profile = singleProfile(ctx);
        Permission perm = permission(ctx);
        if (perm.scope() != Permission.Scope.PLAYER) {
            return fail(ctx.getSource(), "dioaddons_claims.error.flag_not_for_members");
        }
        ClaimMember member = claim.member(profile.getId()).orElse(null);
        if (member == null) {
            return fail(ctx.getSource(), ClaimResult.MEMBER_NOT_FOUND.translationKey());
        }
        boolean allow = BoolArgumentType.getBool(ctx, "allow");
        EnumSet<Permission> perms = member.permissions();
        if (allow) {
            perms.add(perm);
        } else {
            perms.remove(perm);
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ClaimResult r = service().store().setMember(service().actor(player), claim.id(), member.id(), member.name(),
                perms, ClaimsConfig.limits()).result();
        return result(ctx.getSource(), r, allow ? "dioaddons_claims.cmd.member_allowed" : "dioaddons_claims.cmd.member_denied",
                ServerLang.tr(ctx.getSource(), perm.translationKey()), member.name());
    }

    // ---------------------------------------------------------------- admin

    private static int bypass(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        boolean on = service().toggleBypass(ctx.getSource().getPlayerOrException());
        return ok(ctx.getSource(), on ? "dioaddons_claims.admin.bypass_on" : "dioaddons_claims.admin.bypass_off");
    }

    private static int adminListAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        Collection<Claim> all = service().store().all();
        src.sendSuccess(() -> ServerLang.tr(src, "dioaddons_claims.admin.list_header", all.size()), false);
        for (Claim c : all) {
            src.sendSuccess(() -> describe(src, c), false);
        }
        return all.size();
    }

    private static int adminListPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return list(ctx, singleProfile(ctx).getId());
    }

    private static int adminDelete(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Claim claim = claimHere(player);
        if (claim == null) {
            return fail(ctx.getSource(), "dioaddons_claims.error.not_in_claim");
        }
        ClaimStore.Actor admin = new ClaimStore.Actor(player.getUUID(), player.getGameProfile().getName(), true);
        ClaimResult r = service().store().delete(admin, claim.id()).result();
        return result(ctx.getSource(), r, "dioaddons_claims.cmd.deleted", claim.name());
    }

    private static int adminUnclaim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ChunkPos chunk = player.chunkPosition();
        ClaimStore.Actor admin = new ClaimStore.Actor(player.getUUID(), player.getGameProfile().getName(), true);
        ClaimResult r = service().store().unclaimChunk(admin, ClaimsService.dimensionId(player.level()), chunk.toLong(),
                ClaimsConfig.limits()).result();
        return result(ctx.getSource(), r, "dioaddons_claims.cmd.unclaimed", chunk.x, chunk.z);
    }
}
