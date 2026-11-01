package io.github.dioramaaddons.claimsapi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModIdTest {
    @Test
    void modIdIsValidAndPrefixed() {
        assertTrue(ClaimsApiMod.MOD_ID.matches("^[a-z][a-z0-9_]{1,63}$"));
        assertTrue(ClaimsApiMod.MOD_ID.startsWith("dioaddons_"));
    }
}
