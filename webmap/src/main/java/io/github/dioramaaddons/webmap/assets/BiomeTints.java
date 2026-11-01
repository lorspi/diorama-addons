package io.github.dioramaaddons.webmap.assets;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;

/** Grass, foliage and water colours per biome, from the biome's climate and the vanilla colour maps. */
public final class BiomeTints {
    private static final int DEFAULT_GRASS = 0x91BD59;
    private static final int DEFAULT_FOLIAGE = 0x77AB2F;
    private static final int DEFAULT_WATER = 0x3F76E4;

    private final Registry<Biome> biomes;
    private final int[] grassMap;
    private final int[] foliageMap;
    private final Map<String, int[]> cache = new ConcurrentHashMap<>();

    public BiomeTints(MinecraftServer server, AssetSource assets) {
        this.biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        this.grassMap = colormap(assets, "assets/minecraft/textures/colormap/grass.png");
        this.foliageMap = colormap(assets, "assets/minecraft/textures/colormap/foliage.png");
    }

    private static int[] colormap(AssetSource assets, String path) {
        Optional<InputStream> in = assets.open(path);
        if (in.isEmpty()) {
            return null;
        }
        try (InputStream s = in.get()) {
            BufferedImage img = ImageIO.read(s);
            if (img == null || img.getWidth() != 256 || img.getHeight() != 256) {
                return null;
            }
            return img.getRGB(0, 0, 256, 256, null, 0, 256);
        } catch (Exception e) {
            return null;
        }
    }

    /** Vanilla's colour-map lookup by temperature and downfall. */
    static int sample(int[] map, float temperature, float downfall, int fallback) {
        if (map == null) {
            return fallback;
        }
        double t = Math.max(0, Math.min(1, temperature));
        double d = Math.max(0, Math.min(1, downfall)) * t;
        int i = (int) ((1 - t) * 255);
        int j = (int) ((1 - d) * 255);
        int idx = j << 8 | i;
        return idx < map.length ? map[idx] & 0xFFFFFF : fallback;
    }

    /** {grass, foliage, water} as sRGB 0xRRGGBB. */
    public int[] of(String biome) {
        return cache.computeIfAbsent(biome, this::compute);
    }

    private int[] compute(String name) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        Biome b = id == null ? null : biomes.get(id);
        if (b == null) {
            return new int[] {DEFAULT_GRASS, DEFAULT_FOLIAGE, DEFAULT_WATER};
        }
        BiomeSpecialEffects fx = b.getModifiedSpecialEffects();
        Biome.ClimateSettings climate = b.getModifiedClimateSettings();
        int grass = fx.getGrassColorOverride().orElseGet(
                () -> sample(grassMap, climate.temperature(), climate.downfall(), DEFAULT_GRASS));
        switch (fx.getGrassColorModifier().getSerializedName()) {
            case "dark_forest" -> grass = ((grass & 0xFEFEFE) + 0x28340A) >> 1;
            case "swamp" -> grass = 0x6A7039;
            default -> { }
        }
        int foliage = fx.getFoliageColorOverride().orElseGet(
                () -> sample(foliageMap, climate.temperature(), climate.downfall(), DEFAULT_FOLIAGE));
        return new int[] {grass, foliage, fx.getWaterColor()};
    }
}
