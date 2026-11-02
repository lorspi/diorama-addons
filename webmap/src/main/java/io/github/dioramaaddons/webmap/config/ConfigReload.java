package io.github.dioramaaddons.webmap.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import io.github.dioramaaddons.webmap.WebMapMod;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;

/**
 * Reads the config file again on request ({@code /webmap reload}). NeoForge normally does this by itself when
 * the file changes, but its file watcher gets no events on some file systems (network drives, WSL mounts of
 * Windows folders), so admins have a manual way.
 */
public final class ConfigReload {
    private ConfigReload() {}

    /** Returns null on success, otherwise a short reason. */
    public static String reload() {
        ModConfig config = ModConfigs.getModConfigs(WebMapMod.MOD_ID).stream()
                .filter(c -> c.getSpec() == WebMapConfig.SPEC).findFirst().orElse(null);
        IConfigSpec.ILoadedConfig loaded = config == null ? null : config.getLoadedConfig();
        Path path = config == null ? null : config.getFullPath();
        if (loaded == null || path == null) {
            return "config not loaded";
        }
        CommentedConfig parsed;
        try (Reader in = Files.newBufferedReader(path)) {
            parsed = TomlFormat.instance().createParser().parse(in);
        } catch (Exception e) {
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
        // Invalid or missing values fall back to their defaults, as on start-up.
        WebMapConfig.SPEC.correct(parsed);
        CommentedConfig target = loaded.config();
        for (String key : parsed.valueMap().keySet()) {
            target.set(List.of(key), parsed.get(List.of(key)));
        }
        WebMapConfig.SPEC.afterReload();
        return null;
    }
}
