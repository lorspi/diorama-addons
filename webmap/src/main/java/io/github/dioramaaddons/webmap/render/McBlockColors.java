package io.github.dioramaaddons.webmap.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;

/** Block colours from vanilla map colours ({@link Block#defaultMapColor()}). Safe to use from any thread. */
public final class McBlockColors implements BlockColorSource {
    private record Info(int color, boolean water) {}

    private final Map<String, Info> cache = new ConcurrentHashMap<>();

    private Info info(String id) {
        return cache.computeIfAbsent(id, key -> {
            ResourceLocation rl = ResourceLocation.tryParse(key);
            Block block = rl == null ? null : BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
            if (block == null) {
                return new Info(-1, false);
            }
            boolean water = block.defaultBlockState().getFluidState().is(FluidTags.WATER);
            MapColor color = block.defaultMapColor();
            return new Info(color == MapColor.NONE ? -1 : color.col, water);
        });
    }

    @Override
    public int color(String blockId) {
        return info(blockId).color();
    }

    @Override
    public boolean isWater(String blockId) {
        return info(blockId).water();
    }

    @Override
    public int waterColor() {
        return MapColor.WATER.col;
    }
}
