package io.github.dioramaaddons.webmap.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dioramaaddons.claimsapi.ChunkCoord;
import io.github.dioramaaddons.claimsapi.ClaimChangeListener;
import io.github.dioramaaddons.claimsapi.ClaimInfo;
import io.github.dioramaaddons.claimsapi.ClaimsApi;
import io.github.dioramaaddons.claimsapi.ClaimsApiProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClaimsLayerTest {
    private static final class FakeApi implements ClaimsApi {
        final List<ClaimInfo> claims = new ArrayList<>();
        final List<ClaimChangeListener> listeners = new ArrayList<>();

        @Override
        public Optional<ClaimInfo> claimAt(String dimension, int chunkX, int chunkZ) {
            return Optional.empty();
        }

        @Override
        public Collection<ClaimInfo> claimsIn(String dimension) {
            return claims;
        }

        @Override
        public Collection<ClaimInfo> allClaims() {
            return List.copyOf(claims);
        }

        @Override
        public void addListener(ClaimChangeListener l) {
            listeners.add(l);
        }

        @Override
        public void removeListener(ClaimChangeListener l) {
            listeners.remove(l);
        }

        void add(ClaimInfo c) {
            claims.add(c);
            listeners.forEach(l -> l.onClaimChanged(new ClaimChangeListener.ClaimChange(ClaimChangeListener.Type.CREATED, c)));
        }
    }

    private FakeApi api;

    @AfterEach
    void tearDown() {
        if (api != null) {
            ClaimsApiProvider.unregister(api);
        }
    }

    private static ClaimInfo claim(String name) {
        return new ClaimInfo(UUID.randomUUID(), name, UUID.randomUUID(), "Steve", "minecraft:overworld",
                Set.of(ChunkCoord.pack(1, 2)));
    }

    @Test
    void unavailableWithoutProvider() {
        ClaimsLayer layer = new ClaimsLayer();
        layer.start();
        layer.tick();
        assertFalse(layer.available());
        assertEquals("[]", layer.json());
    }

    @Test
    void followsProviderAndChanges() {
        api = new FakeApi();
        api.claims.add(claim("Home"));
        ClaimsApiProvider.register(api);
        ClaimsLayer layer = new ClaimsLayer();
        layer.start();
        layer.tick();
        assertTrue(layer.available());
        assertTrue(layer.json().contains("\"name\":\"Home\""));

        api.add(claim("Farm"));
        layer.tick();
        assertTrue(layer.json().contains("\"name\":\"Farm\""));

        // The provider goes away (server module switched off): noticed within the refresh period.
        ClaimsApiProvider.unregister(api);
        boolean changed = false;
        for (int i = 0; i < 100; i++) {
            changed |= layer.tick();
        }
        assertTrue(changed);
        assertFalse(layer.available());
        assertEquals("[]", layer.json());
    }
}
