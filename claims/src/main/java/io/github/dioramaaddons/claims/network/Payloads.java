package io.github.dioramaaddons.claims.network;

import io.github.dioramaaddons.claims.ClaimsMod;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

final class Payloads {
    private Payloads() {}

    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ClaimsMod.MOD_ID, path));
    }
}
