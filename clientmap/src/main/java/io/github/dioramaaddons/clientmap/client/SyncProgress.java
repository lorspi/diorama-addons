package io.github.dioramaaddons.clientmap.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

/**
 * How far the explored-map sync got, per server, dimension and pool, so a reconnect continues where it
 * stopped. Stored in {@code <game dir>/dioaddons_clientmap/sync.json}.
 */
final class SyncProgress {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static Map<String, Integer> progress;
    private static boolean dirty;

    private SyncProgress() {}

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("dioaddons_clientmap").resolve("sync.json");
    }

    private static Map<String, Integer> map() {
        if (progress == null) {
            progress = new HashMap<>();
            Path f = file();
            if (Files.isRegularFile(f)) {
                try {
                    Map<String, Integer> read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8),
                            new TypeToken<Map<String, Integer>>() {}.getType());
                    if (read != null) {
                        progress.putAll(read);
                    }
                } catch (IOException | RuntimeException e) {
                    LOGGER.warn("Could not read {}", f, e);
                }
            }
        }
        return progress;
    }

    static String key(String server, String dimension, String scope) {
        return server + "|" + dimension + "|" + scope;
    }

    static int get(String key) {
        return map().getOrDefault(key, 0);
    }

    static void set(String key, int value) {
        map().put(key, value);
        dirty = true;
    }

    /** Forgets everything stored for one server. */
    static void reset(String server) {
        map().keySet().removeIf(k -> k.startsWith(server + "|"));
        dirty = true;
        save();
    }

    static void save() {
        if (!dirty || progress == null) {
            return;
        }
        dirty = false;
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("sync.json.tmp");
            Files.writeString(tmp, GSON.toJson(progress), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.warn("Could not write {}", f, e);
        }
    }
}
