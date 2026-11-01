package io.github.dioramaaddons.webmap.assets;

import io.github.dioramaaddons.render.ColorMath;
import io.github.dioramaaddons.render.LookTable;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

/**
 * Turns saved block names into {@link LookTable} ids: colours averaged from the block's top and south face
 * textures (vanilla from the client jar, modded from the mod jars), the way the Diorama map does on the
 * client. Falls back to vanilla map colours when textures are missing. Render thread only.
 */
public final class ServerBlockLooks {
    private final LookTable table = new LookTable();
    private final AssetSource assets;
    private final ModelResolver models;
    private final Map<String, Integer> ids = new HashMap<>();
    private final Map<String, float[]> textures = new HashMap<>();

    public ServerBlockLooks(AssetSource assets) {
        this.assets = assets;
        this.models = new ModelResolver(assets);
    }

    public LookTable table() {
        return table;
    }

    public int id(String blockName, boolean waterlogged) {
        return ids.computeIfAbsent(waterlogged ? blockName + "+w" : blockName, k -> compute(blockName));
    }

    private int compute(String name) {
        ResourceLocation rl = ResourceLocation.tryParse(name);
        Block block = rl == null ? null : BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
        if (block == null) {
            float[] gray = {0.2F, 0.2F, 0.2F};
            return table.add(LookTable.SOLID, LookTable.TINT_NONE, 1, gray, gray);
        }
        BlockState state = block.defaultBlockState();
        if (state.isAir()) {
            return 0;
        }
        FluidState fluid = state.getFluidState();
        boolean noCollision = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
        if (!fluid.isEmpty() && (block instanceof LiquidBlock || noCollision)) {
            boolean water = fluid.is(FluidTags.WATER);
            float[] c = texture(water ? "minecraft:block/water_still" : "minecraft:block/lava_still");
            if (c == null) {
                c = linear(water ? 0xB0B0B0 : 0xD45A12);
            }
            return table.add(water ? LookTable.WATER : LookTable.GLOW, water ? LookTable.TINT_WATER : LookTable.TINT_NONE,
                    1, c, c);
        }
        byte kind = noCollision ? LookTable.PARTIAL
                : state.getLightEmission() >= 12 ? LookTable.GLOW : LookTable.SOLID;

        ModelResolver.Face top = null;
        ModelResolver.Face side = null;
        for (String modelId : models.modelsFor(name)) {
            Optional<ModelResolver.Model> m = models.model(modelId);
            if (m.isEmpty()) {
                continue;
            }
            if (top == null) {
                top = ModelResolver.face(m.get(), "up").orElse(null);
            }
            if (side == null) {
                side = ModelResolver.face(m.get(), "south").or(() -> ModelResolver.face(m.get(), "north")).orElse(null);
            }
            if (top == null && side == null) {
                top = ModelResolver.anyFace(m.get()).orElse(null);
            }
        }
        if (top == null) {
            top = side;
        }
        if (side == null) {
            side = top;
        }
        float[] topColor = top == null ? null : texture(top.texture());
        float[] sideColor = side == null ? null : texture(side.texture());
        if (topColor == null) {
            MapColor mc = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (mc == MapColor.NONE) {
                return kind == LookTable.PARTIAL ? 0 : table.add(kind, LookTable.TINT_NONE, 1, linear(0x808080), linear(0x707070));
            }
            float[] c = linear(mc.col);
            return table.add(kind, LookTable.TINT_NONE, kind == LookTable.PARTIAL ? 0.4F : 1, c, scale(c, 0.85F));
        }
        if (sideColor == null) {
            sideColor = topColor;
        }
        byte tint = LookTable.TINT_NONE;
        if (top.tinted()) {
            String path = rl.getPath();
            int fixed = switch (path) {
                case "birch_leaves" -> 0x80A755;
                case "spruce_leaves" -> 0x619961;
                case "mangrove_leaves" -> 0x92C648;
                case "lily_pad" -> 0x208030;
                default -> -1;
            };
            if (fixed != -1) {
                topColor = tinted(topColor, fixed);
                sideColor = side.tinted() ? tinted(sideColor, fixed) : sideColor;
            } else if (path.contains("leaves") || path.contains("vine")) {
                tint = LookTable.TINT_FOLIAGE;
            } else if (path.contains("redstone") || path.contains("stem")) {
                tint = LookTable.TINT_NONE;
            } else {
                tint = LookTable.TINT_GRASS;
            }
        }
        float coverage = kind == LookTable.PARTIAL ? Math.min(0.6F, topColor[3]) : 1;
        // Grass blocks: tinted top, untinted dirt sides.
        return table.add(kind, tint, side.tinted(), coverage, topColor, sideColor);
    }

    private static float[] scale(float[] c, float f) {
        return new float[] {c[0] * f, c[1] * f, c[2] * f};
    }

    private static float[] tinted(float[] c, int rgb) {
        return new float[] {c[0] * ColorMath.toLinear(rgb >> 16), c[1] * ColorMath.toLinear(rgb >> 8),
            c[2] * ColorMath.toLinear(rgb), c.length > 3 ? c[3] : 1};
    }

    private static float[] linear(int rgb) {
        return new float[] {ColorMath.toLinear(rgb >> 16), ColorMath.toLinear(rgb >> 8), ColorMath.toLinear(rgb), 1};
    }

    /** Linear average of a texture's first frame, alpha weighted; [3] = share of opaque texels. */
    private float[] texture(String id) {
        if (textures.containsKey(id)) {
            return textures.get(id);
        }
        float[] result = null;
        int c = id.indexOf(':');
        String ns = c < 0 ? "minecraft" : id.substring(0, c);
        String path = c < 0 ? id : id.substring(c + 1);
        Optional<InputStream> in = assets.open("assets/" + ns + "/textures/" + path + ".png");
        if (in.isPresent()) {
            try (InputStream s = in.get()) {
                BufferedImage img = ImageIO.read(s);
                if (img != null) {
                    int w = img.getWidth();
                    int h = Math.min(img.getHeight(), w); // first frame of animated textures
                    double r = 0, g = 0, b = 0, a = 0;
                    int opaque = 0;
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            int argb = img.getRGB(x, y);
                            int alpha = argb >>> 24;
                            if (alpha == 0) {
                                continue;
                            }
                            double wgt = alpha / 255.0;
                            r += ColorMath.toLinear(argb >> 16) * wgt;
                            g += ColorMath.toLinear(argb >> 8) * wgt;
                            b += ColorMath.toLinear(argb) * wgt;
                            a += wgt;
                            if (alpha > 127) {
                                opaque++;
                            }
                        }
                    }
                    if (a > 0) {
                        result = new float[] {(float) (r / a), (float) (g / a), (float) (b / a), opaque / (float) (w * h)};
                    }
                }
            } catch (Exception ignored) {
                // missing or unreadable texture: fall back
            }
        }
        textures.put(id, result);
        return result;
    }

}
