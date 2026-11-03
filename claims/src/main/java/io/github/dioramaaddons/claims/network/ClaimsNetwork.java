package io.github.dioramaaddons.claims.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers every payload as optional, so players without the mod can still join. Client-bound handlers
 * call into {@code ClientPayloads} through lambdas, so that class is only loaded on the client.
 */
public final class ClaimsNetwork {
    /** Bumped whenever a payload changes: a client and a server with different versions cannot talk. */
    public static final String VERSION = "3";

    private ClaimsNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(VERSION).optional();
        r.playToServer(RequestStatePayload.TYPE, RequestStatePayload.CODEC, ServerPayloads::requestState);
        r.playToServer(UnsubscribePayload.TYPE, UnsubscribePayload.CODEC, ServerPayloads::unsubscribe);
        r.playToServer(ClaimChunksPayload.TYPE, ClaimChunksPayload.CODEC, ServerPayloads::claimChunks);
        r.playToServer(EditClaimPayload.TYPE, EditClaimPayload.CODEC, ServerPayloads::editClaim);
        r.playToServer(ClaimSettingsPayload.TYPE, ClaimSettingsPayload.CODEC, ServerPayloads::claimSettings);
        r.playToServer(AdminActionPayload.TYPE, AdminActionPayload.CODEC, ServerPayloads::adminAction);
        r.playToClient(OpenScreenPayload.TYPE, OpenScreenPayload.CODEC,
                (p, ctx) -> io.github.dioramaaddons.claims.client.ClientPayloads.openScreen());
        r.playToClient(StatePayload.TYPE, StatePayload.CODEC,
                (p, ctx) -> io.github.dioramaaddons.claims.client.ClientPayloads.state(p));
    }
}
