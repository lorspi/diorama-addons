package io.github.dioramaaddons.claims.client.terrain;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.dioramaaddons.render.ColorMath;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a block looks like from above: the average colour of the texture on its top face, taken from the
 * game's own block models (so resource packs and modded blocks work), like the Diorama map does. Client
 * thread only; cached per block state until resources reload.
 */
final class BlockLooks {
    /** @param r linear red of the untinted top texture @param coverage share of opaque texels (plants are low) */
    record Look(float r, float g, float b, float coverage, int tintIndex) {
        boolean tinted() {
            return tintIndex >= 0;
        }
    }

    static final Look NONE = new Look(0, 0, 0, 0, -1);

    private static final Map<BlockState, Look> CACHE = new IdentityHashMap<>();
    private static final Map<TextureAtlasSprite, float[]> SPRITES = new IdentityHashMap<>();
    private static Object resourcesKey;

    private BlockLooks() {}

    static Look of(BlockState state) {
        Object key = Minecraft.getInstance().getModelManager();
        if (key != resourcesKey) {
            CACHE.clear();
            SPRITES.clear();
            resourcesKey = key;
        }
        return CACHE.computeIfAbsent(state, BlockLooks::compute);
    }

    private static Look compute(BlockState state) {
        if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) {
            return NONE;
        }
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        RandomSource random = RandomSource.create(42);
        BakedQuad top = firstUp(model.getQuads(state, Direction.UP, random));
        if (top == null) {
            top = firstUp(model.getQuads(state, null, random));
        }
        TextureAtlasSprite sprite = top != null ? top.getSprite() : model.getParticleIcon();
        float[] avg = SPRITES.computeIfAbsent(sprite, BlockLooks::average);
        // Without a face pointing up (plants, rails...) only part of the block is seen from above.
        float coverage = top == null ? Math.min(avg[3], 0.45F) : avg[3];
        int tint = top != null && top.isTinted() ? top.getTintIndex() : -1;
        if (top == null && !model.getQuads(state, null, random).isEmpty() && model.getQuads(state, null, random).get(0).isTinted()) {
            tint = model.getQuads(state, null, random).get(0).getTintIndex();
        }
        return new Look(avg[0], avg[1], avg[2], coverage, tint);
    }

    private static BakedQuad firstUp(List<BakedQuad> quads) {
        for (BakedQuad q : quads) {
            if (q.getDirection() == Direction.UP) {
                return q;
            }
        }
        return null;
    }

    /** Linear average of the first animation frame, weighted by alpha; [3] = opaque share. */
    static float[] average(TextureAtlasSprite sprite) {
        SpriteContents contents = sprite.contents();
        NativeImage image = contents.getOriginalImage();
        int w = Math.min(contents.width(), image.getWidth());
        int h = Math.min(contents.height(), image.getHeight());
        double r = 0, g = 0, b = 0, a = 0;
        int opaque = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int abgr = image.getPixelRGBA(x, y);
                int alpha = abgr >>> 24;
                if (alpha == 0) {
                    continue;
                }
                double wgt = alpha / 255.0;
                r += ColorMath.toLinear(abgr & 0xFF) * wgt;
                g += ColorMath.toLinear(abgr >> 8 & 0xFF) * wgt;
                b += ColorMath.toLinear(abgr >> 16 & 0xFF) * wgt;
                a += wgt;
                if (alpha > 127) {
                    opaque++;
                }
            }
        }
        if (a == 0) {
            return new float[] {0, 0, 0, 0};
        }
        return new float[] {(float) (r / a), (float) (g / a), (float) (b / a), opaque / (float) (w * h)};
    }
}
