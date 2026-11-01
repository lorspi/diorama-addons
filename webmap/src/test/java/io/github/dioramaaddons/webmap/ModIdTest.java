package io.github.dioramaaddons.webmap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModIdTest {
    @Test
    void modIdIsValidAndPrefixed() {
        assertTrue(WebMapMod.MOD_ID.matches("^[a-z][a-z0-9_]{1,63}$"));
        assertTrue(WebMapMod.MOD_ID.startsWith("dioaddons_"));
    }
}
