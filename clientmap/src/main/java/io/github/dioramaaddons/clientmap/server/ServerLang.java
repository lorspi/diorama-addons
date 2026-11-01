package io.github.dioramaaddons.clientmap.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** Server-side translations (clients do not need this mod): translatable text with a translated fallback. */
public final class ServerLang {
    private static final Map<String, Map<String, String>> LANGS = new ConcurrentHashMap<>();

    private ServerLang() {}

    private static Map<String, String> lang(String code) {
        return LANGS.computeIfAbsent(code, c -> {
            Map<String, String> map = new HashMap<>();
            try (InputStream in = ServerLang.class.getResourceAsStream("/assets/" + io.github.dioramaaddons.clientmap.ClientMapMod.MOD_ID + "/lang/" + c + ".json")) {
                if (in != null) {
                    JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    obj.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
                }
            } catch (Exception ignored) {
                // fall back to English
            }
            return map;
        });
    }

    public static MutableComponent tr(CommandSourceStack src, String key, Object... args) {
        return tr(src.getPlayer(), key, args);
    }

    public static MutableComponent tr(ServerPlayer player, String key, Object... args) {
        String code = player == null ? "en_us" : player.clientInformation().language().toLowerCase(Locale.ROOT);
        String text = code.startsWith("es_") ? lang("es_es").get(key) : null;
        if (text == null) {
            text = lang("en_us").getOrDefault(key, key);
        }
        return Component.translatableWithFallback(key, text, args);
    }
}
