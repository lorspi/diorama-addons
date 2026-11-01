package io.github.dioramaaddons.webmap.assets;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import org.slf4j.Logger;

/**
 * Reads files under {@code assets/} from the installed mods (which ship their own textures even on a
 * server) and from the vanilla client jar. Mods are searched first.
 */
public final class AssetSource implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final List<Path> roots = new ArrayList<>();
    private FileSystem clientJar;

    public AssetSource(Optional<Path> vanillaJar) {
        for (IModFileInfo info : ModList.get().getModFiles()) {
            try {
                Path assets = info.getFile().findResource("assets");
                if (assets != null && Files.isDirectory(assets)) {
                    roots.add(assets.getParent() == null ? assets.getRoot() : assets.getParent());
                }
            } catch (RuntimeException e) {
                LOGGER.debug("Skipping assets of {}", info.getFile().getFilePath(), e);
            }
        }
        if (vanillaJar.isPresent()) {
            try {
                clientJar = FileSystems.newFileSystem(vanillaJar.get());
                roots.add(clientJar.getPath("/"));
            } catch (IOException e) {
                LOGGER.warn("Web map: could not open {}", vanillaJar.get(), e);
            }
        }
    }

    /** True when vanilla textures are available. */
    public boolean hasVanilla() {
        return clientJar != null;
    }

    /** @param path relative path such as {@code assets/minecraft/models/block/stone.json} */
    public Optional<InputStream> open(String path) {
        for (Path root : roots) {
            Path p = root.resolve(path);
            if (Files.isRegularFile(p)) {
                try {
                    return Optional.of(Files.newInputStream(p));
                } catch (IOException e) {
                    LOGGER.debug("Could not read {}", p, e);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void close() {
        if (clientJar != null) {
            try {
                clientJar.close();
            } catch (IOException ignored) {
                // nothing to do
            }
            clientJar = null;
        }
    }
}
