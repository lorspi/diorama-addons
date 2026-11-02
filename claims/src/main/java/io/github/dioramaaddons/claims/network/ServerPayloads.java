package io.github.dioramaaddons.claims.network;

import io.github.dioramaaddons.claims.config.ClaimsConfig;
import io.github.dioramaaddons.claims.core.ClaimResult;
import io.github.dioramaaddons.claims.core.ClaimStore;
import io.github.dioramaaddons.claims.core.Permission;
import io.github.dioramaaddons.claims.server.ClaimsService;
import io.github.dioramaaddons.claimsapi.ChunkCoord;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server handlers. Never trust the client: every request is checked again here (ownership and permissions
 * inside {@link ClaimStore}, distance and sizes here). Handlers run on the server thread (the registrar
 * default).
 */
final class ServerPayloads {
    private ServerPayloads() {}

    private static ServerPlayer player(IPayloadContext ctx) {
        return ctx.player() instanceof ServerPlayer sp ? sp : null;
    }

    static void requestState(RequestStatePayload p, IPayloadContext ctx) {
        ClaimsService service = ClaimsService.get();
        ServerPlayer player = player(ctx);
        if (service != null && player != null) {
            service.subscribe(player, p.radius());
        }
    }

    static void unsubscribe(UnsubscribePayload p, IPayloadContext ctx) {
        ClaimsService service = ClaimsService.get();
        ServerPlayer player = player(ctx);
        if (service != null && player != null) {
            service.unsubscribe(player);
        }
    }

    static void claimChunks(ClaimChunksPayload p, IPayloadContext ctx) {
        ClaimsService service = ClaimsService.get();
        ServerPlayer player = player(ctx);
        if (service == null || player == null) {
            return;
        }
        int maxRadius = ClaimsConfig.SERVER.guiMaxRadius.get();
        ChunkPos here = player.chunkPosition();
        List<Long> chunks = new ArrayList<>();
        for (long c : p.chunks()) {
            int dx = Math.abs(ChunkCoord.unpackX(c) - here.x);
            int dz = Math.abs(ChunkCoord.unpackZ(c) - here.z);
            if (dx <= maxRadius && dz <= maxRadius) {
                chunks.add(c);
            }
        }
        if (chunks.isEmpty()) {
            service.sendState(player, new StatePayload.Feedback(false, "dioaddons_claims.error.too_far",
                    List.of(String.valueOf(maxRadius))));
            return;
        }
        ServerLevel level = player.serverLevel();
        String dim = ClaimsService.dimensionId(level);
        ClaimStore.Actor actor = service.actor(player);
        ClaimStore.BatchOutcome outcome = p.claim()
                ? service.store().claimChunks(actor, dim, chunks, p.target().orElse(null), ClaimsConfig.limits(),
                        service.spawnChunk(level))
                : service.store().unclaimChunks(actor, dim, chunks, ClaimsConfig.limits());
        StatePayload.Feedback fb;
        if (outcome.firstError().ok()) {
            fb = new StatePayload.Feedback(true, p.claim() ? "dioaddons_claims.gui.claimed" : "dioaddons_claims.gui.released",
                    List.of(String.valueOf(outcome.changed())));
        } else {
            fb = feedback(outcome.firstError(), outcome.changed());
        }
        service.sendState(player, fb);
    }

    static void editClaim(EditClaimPayload p, IPayloadContext ctx) {
        ClaimsService service = ClaimsService.get();
        ServerPlayer player = player(ctx);
        if (service == null || player == null) {
            return;
        }
        ClaimStore store = service.store();
        ClaimStore.Actor actor = service.actor(player);
        EnumSet<Permission> perms = Permission.fromMask(p.mask());
        ClaimResult result;
        switch (p.op()) {
            case RENAME -> result = store.rename(actor, p.claimId(), p.text()).result();
            case DELETE -> result = store.delete(actor, p.claimId()).result();
            case ADD_MEMBER -> {
                var profile = service.findProfile(p.text().trim());
                if (profile.isEmpty()) {
                    service.sendState(player, new StatePayload.Feedback(false,
                            "dioaddons_claims.error.player_not_found", List.of(p.text().trim())));
                    return;
                }
                result = store.setMember(actor, p.claimId(), profile.get().getId(), profile.get().getName(), perms,
                        ClaimsConfig.limits()).result();
            }
            case SET_MEMBER -> {
                if (p.player().isEmpty()) {
                    return;
                }
                var claim = store.claim(p.claimId()).orElse(null);
                var member = claim == null ? null : claim.member(p.player().get()).orElse(null);
                if (member == null) {
                    result = claim == null ? ClaimResult.CLAIM_NOT_FOUND : ClaimResult.MEMBER_NOT_FOUND;
                } else {
                    result = store.setMember(actor, p.claimId(), member.id(), member.name(), perms,
                            ClaimsConfig.limits()).result();
                }
            }
            case REMOVE_MEMBER -> {
                if (p.player().isEmpty()) {
                    return;
                }
                result = store.removeMember(actor, p.claimId(), p.player().get()).result();
            }
            case SET_PUBLIC -> result = store.setPublicPermissions(actor, p.claimId(), perms).result();
            default -> result = ClaimResult.CLAIM_NOT_FOUND;
        }
        service.sendState(player, result.ok()
                ? new StatePayload.Feedback(true, "dioaddons_claims.gui.saved", List.of())
                : feedback(result, 0));
    }

    static void adminAction(AdminActionPayload p, IPayloadContext ctx) {
        ClaimsService service = ClaimsService.get();
        ServerPlayer player = player(ctx);
        if (service == null || player == null) {
            return;
        }
        if (!service.isAdmin(player)) {
            service.sendState(player, new StatePayload.Feedback(false, "dioaddons_claims.error.not_admin", List.of()));
            return;
        }
        switch (p.op()) {
            case TOGGLE_ADMIN_MODE -> {
                boolean on = service.toggleBypass(player);
                service.sendState(player, new StatePayload.Feedback(true,
                        on ? "dioaddons_claims.admin.bypass_on" : "dioaddons_claims.admin.bypass_off", List.of()));
            }
            case TELEPORT -> {
                var claim = p.claimId().flatMap(id -> service.store().claim(id)).orElse(null);
                if (claim == null) {
                    service.sendState(player, feedback(ClaimResult.CLAIM_NOT_FOUND, 0));
                    return;
                }
                ResourceLocation dimId = ResourceLocation.tryParse(claim.dimension());
                ServerLevel level = dimId == null ? null
                        : service.server().getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
                if (level == null || claim.chunks().isEmpty()) {
                    service.sendState(player, feedback(ClaimResult.CLAIM_NOT_FOUND, 0));
                    return;
                }
                long chunk = centralChunk(claim.chunks());
                int x = ChunkCoord.unpackX(chunk) * 16 + 8;
                int z = ChunkCoord.unpackZ(chunk) * 16 + 8;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (y <= level.getMinBuildHeight()) {
                    y = level.getSharedSpawnPos().getY();
                }
                player.teleportTo(level, x + 0.5, y, z + 0.5, player.getYRot(), player.getXRot());
                service.sendState(player, new StatePayload.Feedback(true, "dioaddons_claims.admin.teleported",
                        List.of(claim.name(), claim.ownerName())));
            }
            case TRANSFER -> {
                var claim = p.claimId().flatMap(id -> service.store().claim(id)).orElse(null);
                var profile = service.findProfile(p.text().trim());
                if (profile.isEmpty()) {
                    service.sendState(player, new StatePayload.Feedback(false,
                            "dioaddons_claims.error.player_not_found", List.of(p.text().trim())));
                    return;
                }
                ClaimResult r = claim == null ? ClaimResult.CLAIM_NOT_FOUND
                        : service.store().transfer(service.actor(player), claim.id(), profile.get().getId(),
                                profile.get().getName()).result();
                service.sendState(player, r.ok()
                        ? new StatePayload.Feedback(true, "dioaddons_claims.admin.transferred",
                                List.of(claim.name(), profile.get().getName()))
                        : feedback(r, 0));
            }
        }
    }

    /** The claimed chunk nearest to the middle of the claim. */
    static long centralChunk(java.util.Collection<Long> chunks) {
        double cx = 0;
        double cz = 0;
        for (long c : chunks) {
            cx += ChunkCoord.unpackX(c);
            cz += ChunkCoord.unpackZ(c);
        }
        cx /= chunks.size();
        cz /= chunks.size();
        long best = chunks.iterator().next();
        double bestD = Double.MAX_VALUE;
        for (long c : chunks) {
            double d = Math.pow(ChunkCoord.unpackX(c) - cx, 2) + Math.pow(ChunkCoord.unpackZ(c) - cz, 2);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    static StatePayload.Feedback feedback(ClaimResult result, int changed) {
        return new StatePayload.Feedback(false, result.translationKey(), List.of(String.valueOf(changed)));
    }
}
