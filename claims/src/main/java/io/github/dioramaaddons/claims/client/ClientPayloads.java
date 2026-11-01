package io.github.dioramaaddons.claims.client;

import io.github.dioramaaddons.claims.network.StatePayload;
import net.minecraft.client.Minecraft;

/** Client-bound payload handlers. Runs on the client main thread. */
public final class ClientPayloads {
    private ClientPayloads() {}

    public static void openScreen() {
        ClaimsClient.openScreen();
    }

    public static void state(StatePayload state) {
        if (Minecraft.getInstance().screen instanceof ClaimsScreen screen) {
            screen.onState(state);
        }
    }
}
