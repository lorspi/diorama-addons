package io.github.dioramaaddons.claims.client.terrain;

import io.github.dioramaaddons.render.ColorMath;
import io.github.dioramaaddons.render.TopDownScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * Reads the chunks the client has loaded into a {@link TopDownScene}. Client thread only; the shading
 * afterwards can run on another thread.
 */
public final class TerrainSampler {
    private static final int MAX_DESCENT = 48;
    private static final int MAX_WATER = 24;

    private TerrainSampler() {}

    public static TopDownScene sample(ClientLevel level, int minX, int minZ, int width, int depth) {
        TopDownScene scene = new TopDownScene(width, depth);
        BlockColors blockColors = Minecraft.getInstance().getBlockColors();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cz = minZ >> 4; cz <= (minZ + depth - 1) >> 4; cz++) {
            for (int cx = minX >> 4; cx <= (minX + width - 1) >> 4; cx++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null || chunk.isEmpty()) {
                    continue;
                }
                for (int lz = 0; lz < 16; lz++) {
                    int z = (cz << 4) + lz;
                    if (z < minZ || z >= minZ + depth) {
                        continue;
                    }
                    for (int lx = 0; lx < 16; lx++) {
                        int x = (cx << 4) + lx;
                        if (x < minX || x >= minX + width) {
                            continue;
                        }
                        column(level, chunk, blockColors, pos, x, z, scene, x - minX, z - minZ);
                    }
                }
            }
        }
        return scene;
    }

    private static void column(ClientLevel level, LevelChunk chunk, BlockColors colors, BlockPos.MutableBlockPos pos,
            int x, int z, TopDownScene scene, int sx, int sz) {
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int bottom = Math.max(level.getMinBuildHeight(), top - MAX_DESCENT);
        float pr = 0, pg = 0, pb = 0, cover = 0;
        int waterTop = Integer.MIN_VALUE;
        int waterDepth = 0;
        float[] waterColor = null;
        for (int y = top; y >= bottom; y--) {
            pos.set(x, y, z);
            var state = chunk.getBlockState(pos);
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty() && fluid.is(FluidTags.WATER) && waterDepth < MAX_WATER) {
                if (waterTop == Integer.MIN_VALUE) {
                    waterTop = y;
                    waterColor = fluidColor(level, fluid, pos);
                }
                waterDepth++;
                if (state.isAir() || state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
                    continue;
                }
            }
            if (!fluid.isEmpty() && !fluid.is(FluidTags.WATER) && state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
                // Lava and other fluids: their own texture, glowing when they give light.
                float[] c = fluidColor(level, fluid, pos);
                scene.set(sx, sz, y, c[0], c[1], c[2]);
                scene.glow[scene.index(sx, sz)] = state.getLightEmission(level, pos) >= 10;
                return;
            }
            BlockLooks.Look look = BlockLooks.of(state);
            if (look.coverage() <= 0.02F) {
                continue;
            }
            float r = look.r(), g = look.g(), b = look.b();
            if (look.tinted()) {
                int tint = colors.getColor(state, level, pos, look.tintIndex());
                if (tint != -1) {
                    r *= ColorMath.toLinear(tint >> 16);
                    g *= ColorMath.toLinear(tint >> 8);
                    b *= ColorMath.toLinear(tint);
                }
            }
            float c = Math.min(1, look.coverage()) * (1 - cover);
            pr += r * c;
            pg += g * c;
            pb += b * c;
            cover += c;
            if (cover < 0.9F && look.coverage() < 0.9F) {
                continue; // plants, glass, leaves with holes: look further down
            }
            float rest = 1 - cover;
            // Plants are coloured in, but the ground they stand on gives the height for light and shadow.
            int surface = waterTop != Integer.MIN_VALUE ? waterTop : y;
            scene.set(sx, sz, surface, pr + r * rest, pg + g * rest, pb + b * rest);
            if (waterDepth > 0) {
                scene.setWater(sx, sz, waterDepth, waterColor[0], waterColor[1], waterColor[2]);
            }
            scene.glow[scene.index(sx, sz)] = state.getLightEmission(level, pos) >= 12;
            return;
        }
        if (waterDepth > 0) {
            scene.set(sx, sz, waterTop, waterColor[0] * 0.5F, waterColor[1] * 0.5F, waterColor[2] * 0.5F);
            scene.setWater(sx, sz, waterDepth, waterColor[0], waterColor[1], waterColor[2]);
        }
    }

    private static float[] fluidColor(ClientLevel level, FluidState fluid, BlockPos pos) {
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(ext.getStillTexture(fluid, level, pos));
        float[] avg = BlockLooks.average(sprite);
        int tint = ext.getTintColor(fluid, level, pos);
        return new float[] {
            avg[0] * ColorMath.toLinear(tint >> 16), avg[1] * ColorMath.toLinear(tint >> 8), avg[2] * ColorMath.toLinear(tint)
        };
    }
}
