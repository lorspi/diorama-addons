package io.github.dioramaaddons.claims.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import io.github.dioramaaddons.claims.ClaimsMod;
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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Server-side translations so players without the mod still read proper messages. Messages are sent as
 * translatable components with a fallback in the player's language: clients with the mod use their own
 * language files, vanilla clients show the fallback.
 */
public final class ServerLang {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String[] BUNDLED = {"en_us", "es_es"};
    private static final Map<String, Map<String, String>> LANGS = new ConcurrentHashMap<>();

    private ServerLang() {}

    private static Map<String, String> lang(String code) {
        return LANGS.computeIfAbsent(code, ServerLang::read);
    }

    private static Map<String, String> read(String code) {
        Map<String, String> map = new HashMap<>();
        String path = "/assets/" + ClaimsMod.MOD_ID + "/lang/" + code + ".json";
        try (InputStream in = ServerLang.class.getResourceAsStream(path)) {
            if (in != null) {
                JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                obj.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
            }
        } catch (Exception e) {
            LOGGER.warn("Could not read {}", path, e);
        }
        return map;
    }

    private static String bestLanguage(@Nullable String requested) {
        if (requested == null) {
            return "en_us";
        }
        String code = requested.toLowerCase(Locale.ROOT);
        for (String b : BUNDLED) {
            if (b.equals(code)) {
                return b;
            }
        }
        String prefix = code.contains("_") ? code.substring(0, code.indexOf('_') + 1) : code + "_";
        for (String b : BUNDLED) {
            if (b.startsWith(prefix)) {
                return b;
            }
        }
        return "en_us";
    }

    public static String raw(@Nullable String language, String key) {
        String value = lang(bestLanguage(language)).get(key);
        if (value == null) {
            value = lang("en_us").getOrDefault(key, key);
        }
        return value;
    }

    public static MutableComponent tr(@Nullable ServerPlayer player, String key, Object... args) {
        String language = player == null ? null : player.clientInformation().language();
        return Component.translatableWithFallback(key, raw(language, key), args);
    }

    public static MutableComponent tr(CommandSourceStack source, String key, Object... args) {
        return tr(source.getPlayer(), key, args);
    }
}
