package io.github.dioramaaddons.claims.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import io.github.dioramaaddons.claims.core.Claim;
import io.github.dioramaaddons.claims.core.ClaimLimits;
import io.github.dioramaaddons.claims.core.ClaimStore;
import io.github.dioramaaddons.claims.core.Permission;
import io.github.dioramaaddons.claims.core.PermissionTemplate;
import io.github.dioramaaddons.claimsapi.ChunkCoord;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimCodecsTest {
    static final String DIM = "minecraft:the_nether";

    @Test
    void roundTrip() {
        ClaimStore store = new ClaimStore();
        ClaimStore.Actor alice = new ClaimStore.Actor(UUID.randomUUID(), "Alice", false);
        Claim home = store.claimChunk(alice, DIM, ChunkCoord.pack(-3, 7), null, "Base", ClaimLimits.DEFAULT, null).claim();
        store.claimChunk(alice, DIM, ChunkCoord.pack(-2, 7), null, null, ClaimLimits.DEFAULT, null);
        UUID bob = UUID.randomUUID();
        store.setMember(alice, home.id(), bob, "Bob", PermissionTemplate.VISITOR.permissions(), ClaimLimits.DEFAULT);
        store.setPublicPermissions(alice, home.id(), EnumSet.of(Permission.PVP, Permission.INTERACT_BLOCKS));

        JsonElement json = ClaimCodecs.encode(store, JsonOps.INSTANCE).getOrThrow();
        assertEquals(ClaimCodecs.FORMAT_VERSION, json.getAsJsonObject().get("format_version").getAsInt());

        ClaimStore loaded = ClaimCodecs.decode(new Dynamic<>(JsonOps.INSTANCE, json), new ClaimStore()).getOrThrow();
        Claim c = loaded.claim(home.id()).orElseThrow();
        assertEquals("Base", c.name());
        assertEquals("Alice", c.ownerName());
        assertEquals(alice.id(), c.owner());
        assertEquals(home.chunks(), c.chunks());
        assertEquals(EnumSet.of(Permission.PVP, Permission.INTERACT_BLOCKS), c.publicPermissions());
        assertEquals(PermissionTemplate.VISITOR.permissions(), c.member(bob).orElseThrow().permissions());
        assertEquals(c, loaded.claimAt(DIM, ChunkCoord.pack(-2, 7)));
    }

    @Test
    void unknownPermissionsAreIgnoredAndMissingVersionIsAccepted() {
        String raw = """
                {"claims":[{"id":"%s","name":"Old","owner":"%s","dimension":"minecraft:overworld",
                "chunks":[0],"public":["break_blocks","removed_permission"]}]}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
        JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
        ClaimStore loaded = ClaimCodecs.decode(new Dynamic<>(JsonOps.INSTANCE, json), new ClaimStore()).getOrThrow();
        Claim c = loaded.claimAt("minecraft:overworld", 0L);
        assertEquals(EnumSet.of(Permission.BREAK_BLOCKS), c.publicPermissions());
        assertTrue(c.members().isEmpty());
    }

    @Test
    void newerFormatIsRefused() {
        JsonObject json = JsonParser.parseString("{\"format_version\":999,\"claims\":[]}").getAsJsonObject();
        assertThrows(IllegalStateException.class,
                () -> ClaimCodecs.decode(new Dynamic<>(JsonOps.INSTANCE, json), new ClaimStore()));
    }
}
