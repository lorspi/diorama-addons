package io.github.dioramaaddons.claims.config;

import io.github.dioramaaddons.claims.core.ClaimLimits;
import io.github.dioramaaddons.claims.core.Permission;
import java.util.EnumSet;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class ClaimsConfig {
    public static final Server SERVER;
    public static final ModConfigSpec SERVER_SPEC;
    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        Pair<Server, ModConfigSpec> server = new ModConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();
        Pair<Client, ModConfigSpec> client = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    private ClaimsConfig() {}

    public static final class Server {
        public final ModConfigSpec.IntValue maxChunksPerPlayer;
        public final ModConfigSpec.IntValue maxClaimsPerPlayer;
        public final ModConfigSpec.BooleanValue requireContiguous;
        public final ModConfigSpec.IntValue minSpawnDistance;
        public final ModConfigSpec.IntValue maxMembersPerClaim;
        public final ModConfigSpec.ConfigValue<List<? extends String>> defaultPublicPermissions;
        public final ModConfigSpec.IntValue guiMaxRadius;
        public final ModConfigSpec.IntValue adminPermissionLevel;
        public final ModConfigSpec.IntValue denialMessageCooldownTicks;
        public final ModConfigSpec.BooleanValue greetingsEnabled;
        public final ModConfigSpec.EnumValue<GreetingDisplay> greetingDisplay;
        public final ModConfigSpec.IntValue alarmCooldownSeconds;

        Server(ModConfigSpec.Builder b) {
            b.push("limits");
            maxChunksPerPlayer = b.comment("Maximum number of chunks a player can own, across all of their claims.",
                    "Being a member of other players' claims does not count.")
                    .defineInRange("maxChunksPerPlayer", 100, 0, 1_000_000);
            maxClaimsPerPlayer = b.comment("Maximum number of separate claims a player can own.")
                    .defineInRange("maxClaimsPerPlayer", 10, 0, 10_000);
            requireContiguous = b.comment("If true, the chunks of one claim must share an edge, and releasing a",
                    "chunk that would split a claim in two is refused.")
                    .define("requireContiguous", true);
            minSpawnDistance = b.comment("Minimum distance in chunks between a claimed chunk and the overworld",
                    "spawn chunk. 0 disables the check.")
                    .defineInRange("minSpawnDistance", 0, 0, 10_000);
            maxMembersPerClaim = b.comment("Maximum number of friends that can be added to one claim.")
                    .defineInRange("maxMembersPerClaim", 32, 0, 1_000);
            b.pop();

            b.push("defaults");
            defaultPublicPermissions = b.comment("Public permissions given to newly created claims.",
                    "Valid values: " + String.join(", ", allIds()))
                    .defineListAllowEmpty("defaultPublicPermissions", List.of(),
                            () -> "interact_blocks", o -> o instanceof String s && Permission.byId(s).isPresent());
            b.pop();

            b.push("misc");
            guiMaxRadius = b.comment("Largest grid radius (in chunks) a client may request and edit from the GUI.",
                    "Chunks further than this from the player are rejected by the server.")
                    .defineInRange("guiMaxRadius", 16, 2, 64);
            adminPermissionLevel = b.comment("Permission level required for /claims admin commands and bypass.")
                    .defineInRange("adminPermissionLevel", 2, 0, 4);
            denialMessageCooldownTicks = b.comment("Minimum ticks between two 'you cannot do that here' messages",
                    "sent to the same player.")
                    .defineInRange("denialMessageCooldownTicks", 40, 0, 1200);
            b.pop();

            b.push("greetings");
            greetingsEnabled = b.comment("Owners can set a welcome and a farewell notification for their claims, and a",
                    "visitor alarm. If false, nothing is shown or sent (the settings are kept).")
                    .define("enabled", true);
            greetingDisplay = b.comment("How welcome and farewell notifications are shown: TITLE (title and subtitle",
                    "in the middle of the screen), ACTION_BAR (above the hotbar) or CHAT. All work on vanilla clients.")
                    .defineEnum("display", GreetingDisplay.TITLE);
            alarmCooldownSeconds = b.comment("The visitor alarm tells about the same visitor in the same claim at most",
                    "once in this many seconds.")
                    .defineInRange("alarmCooldownSeconds", 120, 0, 86_400);
            b.pop();
        }
    }

    public static final class Client {
        public final ModConfigSpec.IntValue gridRadius;

        Client(ModConfigSpec.Builder b) {
            gridRadius = b.comment("Radius in chunks of the claims grid (10 = 21x21). The server may lower it.")
                    .defineInRange("gridRadius", 10, 2, 64);
        }
    }

    /** Where claim greetings are shown. */
    public enum GreetingDisplay { TITLE, ACTION_BAR, CHAT }

    /** Server limits only (no player's own limits). */
    public static ClaimLimits limits() {
        return limits(ClaimLimits.PerPlayer.NONE);
    }

    /** Server limits, with each player's own limits (set by an operator) where they have them. */
    public static ClaimLimits limits(ClaimLimits.PerPlayer perPlayer) {
        return new ClaimLimits(SERVER.maxChunksPerPlayer.get(), SERVER.maxClaimsPerPlayer.get(),
                SERVER.requireContiguous.get(), SERVER.minSpawnDistance.get(), SERVER.maxMembersPerClaim.get(),
                perPlayer);
    }

    public static EnumSet<Permission> defaultPublicPermissions() {
        EnumSet<Permission> set = EnumSet.noneOf(Permission.class);
        for (String s : SERVER.defaultPublicPermissions.get()) {
            Permission.byId(s).ifPresent(set::add);
        }
        return set;
    }

    private static List<String> allIds() {
        return java.util.Arrays.stream(Permission.values()).map(Permission::id).toList();
    }
}
