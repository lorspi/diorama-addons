package io.github.dioramaaddons.webmap.render;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.jetbrains.annotations.Nullable;

/** Disk cache: {@code <root>/<dimKey>/<zoom>/<x>_<y>.png}. */
public final class TileStore {
    private final Path root;

    public TileStore(Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }

    /** Dimension id → folder/URL key: {@code minecraft:the_nether} → {@code minecraft~the_nether}. */
    public static String dimKey(String dimensionId) {
        return dimensionId.replace(':', '~').replace('/', '+');
    }

    public Path path(String dimKey, int zoom, int x, int y) {
        return root.resolve(dimKey).resolve(Integer.toString(zoom)).resolve(x + "_" + y + ".png");
    }

    public @Nullable int[] read(String dimKey, int zoom, int x, int y) {
        Path p = path(dimKey, zoom, x, y);
        if (!Files.isRegularFile(p)) {
            return null;
        }
        try {
            BufferedImage img = ImageIO.read(p.toFile());
            if (img == null || img.getWidth() != TileMath.TILE_SIZE || img.getHeight() != TileMath.TILE_SIZE) {
                return null;
            }
            return img.getRGB(0, 0, TileMath.TILE_SIZE, TileMath.TILE_SIZE, null, 0, TileMath.TILE_SIZE);
        } catch (IOException e) {
            return null;
        }
    }

    /** Writes atomically. An all-transparent tile deletes the file instead. */
    public void write(String dimKey, int zoom, int x, int y, int[] argb) throws IOException {
        Path p = path(dimKey, zoom, x, y);
        boolean empty = true;
        for (int px : argb) {
            if (px != 0) {
                empty = false;
                break;
            }
        }
        if (empty) {
            Files.deleteIfExists(p);
            return;
        }
        Files.createDirectories(p.getParent());
        BufferedImage img = new BufferedImage(TileMath.TILE_SIZE, TileMath.TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, TileMath.TILE_SIZE, TileMath.TILE_SIZE, argb, 0, TileMath.TILE_SIZE);
        Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
        ImageIO.write(img, "png", tmp.toFile());
        Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public void clear() throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
