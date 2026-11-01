package io.github.dioramaaddons.clientmap.compat.claims;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/**
 * Claiming from the map uses the claims mod's own payload, so the server validates it exactly like the
 * claims screen. Needs the claims mod on the client (for the payload class) and on the server.
 */
public final class ClaimsCompat {
    public static final String MOD_ID = "dioaddons_claims";
    private static final ResourceLocation CHANNEL = ResourceLocation.fromNamespaceAndPath(MOD_ID, "claim_chunks");

    private ClaimsCompat() {}

    public static boolean available() {
        var connection = Minecraft.getInstance().getConnection();
        return ModList.get().isLoaded(MOD_ID) && connection != null && connection.hasChannel(CHANNEL);
    }

    public static void send(boolean claim, int chunkX, int chunkZ) {
        if (available()) {
            ClaimsPayloadSender.send(claim, chunkX, chunkZ);
        }
    }
}
