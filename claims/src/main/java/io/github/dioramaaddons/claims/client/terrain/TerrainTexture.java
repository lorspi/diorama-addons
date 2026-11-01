package io.github.dioramaaddons.claims.client.terrain;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.dioramaaddons.claims.ClaimsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** The terrain picture under the claims grid, as a GPU texture. Render thread only. */
public final class TerrainTexture implements AutoCloseable {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ClaimsMod.MOD_ID, "dynamic/terrain");

    private DynamicTexture texture;
    private int width;
    private int height;

    public void upload(int[] argb, int w, int h) {
        if (texture == null || width != w || height != h) {
            close();
            texture = new DynamicTexture(w, h, false);
            width = w;
            height = h;
            Minecraft.getInstance().getTextureManager().register(ID, texture);
        }
        NativeImage img = texture.getPixels();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = argb[y * w + x];
                // NativeImage wants ABGR.
                img.setPixelRGBA(x, y, (c & 0xFF00FF00) | (c >> 16 & 0xFF) | (c & 0xFF) << 16);
            }
        }
        texture.upload();
    }

    public boolean ready() {
        return texture != null;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    @Override
    public void close() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(ID);
            texture = null;
        }
    }
}
