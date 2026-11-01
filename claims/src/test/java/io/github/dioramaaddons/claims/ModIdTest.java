package io.github.dioramaaddons.claims;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModIdTest {
    @Test
    void modIdIsValidAndPrefixed() {
        assertTrue(ClaimsMod.MOD_ID.matches("^[a-z][a-z0-9_]{1,63}$"));
        assertTrue(ClaimsMod.MOD_ID.startsWith("dioaddons_"));
    }
}
