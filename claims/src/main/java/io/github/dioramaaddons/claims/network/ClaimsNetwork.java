package io.github.dioramaaddons.claims.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers every payload as optional, so players without the mod can still join. Client-bound handlers
 * call into {@code ClientPayloads} through lambdas, so that class is only loaded on the client.
 */
public final class ClaimsNetwork {
    public static final String VERSION = "1";

    private ClaimsNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(VERSION).optional();
        r.playToServer(RequestStatePayload.TYPE, RequestStatePayload.CODEC, ServerPayloads::requestState);
        r.playToServer(UnsubscribePayload.TYPE, UnsubscribePayload.CODEC, ServerPayloads::unsubscribe);
        r.playToServer(ClaimChunksPayload.TYPE, ClaimChunksPayload.CODEC, ServerPayloads::claimChunks);
        r.playToServer(EditClaimPayload.TYPE, EditClaimPayload.CODEC, ServerPayloads::editClaim);
        r.playToClient(OpenScreenPayload.TYPE, OpenScreenPayload.CODEC,
                (p, ctx) -> io.github.dioramaaddons.claims.client.ClientPayloads.openScreen());
        r.playToClient(StatePayload.TYPE, StatePayload.CODEC,
                (p, ctx) -> io.github.dioramaaddons.claims.client.ClientPayloads.state(p));
    }
}
