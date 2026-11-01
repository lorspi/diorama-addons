package io.github.dioramaaddons.clientmap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModIdTest {
    @Test
    void modIdIsValidAndPrefixed() {
        assertTrue(ClientMapMod.MOD_ID.matches("^[a-z][a-z0-9_]{1,63}$"));
        assertTrue(ClientMapMod.MOD_ID.startsWith("dioaddons_"));
    }
}
