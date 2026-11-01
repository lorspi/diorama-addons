package io.github.dioramaaddons.render;

/**
 * Shades a {@link TopDownScene} with the light model of the Diorama map, adapted to a view from straight
 * above: sun and sky light with cast shadows, sky occlusion, crease occlusion, height shade, clear-ish water,
 * outlines where higher ground starts, and the colour grade. Pure Java, safe on any thread.
 */
public final class TopDownShader {
    /** How far shadows are traced, in blocks. */
    static final int SHADOW_DISTANCE = 48;
    /** How far sky occlusion looks, in blocks. */
    static final int SKY_REACH = 8;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private TopDownShader() {}

    /**
     * @param pixelsPerBlock 1 to 8: how many pixels each block column becomes (outlines are drawn on the
     *                       edge pixels, so more pixels give crisper edges)
     * @return ARGB pixels, {@code width * k} by {@code depth * k}; unknown columns are transparent
     */
    public static int[] shade(TopDownScene s, MapStyle style, int pixelsPerBlock) {
        int k = Math.max(1, Math.min(8, pixelsPerBlock));
        int outW = s.width * k;
        int[] out = new int[outW * s.depth * k];
        double el = Math.toRadians(style.sunElevation());
        double sunX = Math.cos(el) * style.sunAzimuthX();
        double sunY = Math.sin(el);
        double sunZ = Math.cos(el) * style.sunAzimuthZ();
        double horiz = Math.hypot(sunX, sunZ);
        int maxHeight = TopDownScene.UNKNOWN;
        for (int h : s.height) {
            maxHeight = Math.max(maxHeight, h);
        }
        float keep = 1 - style.outline();
        for (int z = 0; z < s.depth; z++) {
            for (int x = 0; x < s.width; x++) {
                int i = s.index(x, z);
                int y = s.height[i];
                if (y == TopDownScene.UNKNOWN) {
                    continue;
                }
                int argb = s.glow[i] ? glow(s, i, style) : lit(s, style, x, z, y, sunX, sunY, sunZ, horiz, maxHeight);
                // Outline where a neighbouring column stands higher, like Diorama's silhouettes: strong at
                // cliffs and walls, faint at single-block steps so gentle slopes do not turn into contour lines.
                int west = step(s.heightAt(x - 1, z), y);
                int east = step(s.heightAt(x + 1, z), y);
                int north = step(s.heightAt(x, z - 1), y);
                int south = step(s.heightAt(x, z + 1), y);
                float strong = k == 1 ? 1 - style.outline() * 0.5F : keep;
                int dark = darken(argb, strong);
                int faint = darken(argb, 1 - (1 - strong) * 0.25F);
                for (int pz = 0; pz < k; pz++) {
                    int row = (z * k + pz) * outW + x * k;
                    for (int px = 0; px < k; px++) {
                        int edge = Math.max(Math.max(px == 0 ? west : 0, px == k - 1 ? east : 0),
                                Math.max(pz == 0 ? north : 0, pz == k - 1 ? south : 0));
                        out[row + px] = edge == 2 ? dark : edge == 1 ? faint : argb;
                    }
                }
            }
        }
        return out;
    }

    /** 0 = not higher, 1 = one block higher, 2 = a cliff or wall. */
    private static int step(int neighbour, int y) {
        if (neighbour == TopDownScene.UNKNOWN || neighbour <= y) {
            return 0;
        }
        return neighbour - y >= 2 ? 2 : 1;
    }

    /**
     * Seen from above, slopes show no side faces, so they are lit by their slope instead: ground facing the
     * sun is brighter, ground facing away darker. 1 on flat ground.
     */
    static float slopeLight(TopDownScene s, int x, int z, int y, double sunX, double sunY, double sunZ) {
        int w = s.heightAt(x - 1, z), e = s.heightAt(x + 1, z), n = s.heightAt(x, z - 1), so = s.heightAt(x, z + 1);
        double dx = ((e == TopDownScene.UNKNOWN ? y : e) - (w == TopDownScene.UNKNOWN ? y : w)) / 2.0;
        double dz = ((so == TopDownScene.UNKNOWN ? y : so) - (n == TopDownScene.UNKNOWN ? y : n)) / 2.0;
        // Clamp so walls (handled by outlines and shadows) do not blow out.
        dx = Math.max(-2, Math.min(2, dx));
        dz = Math.max(-2, Math.min(2, dz));
        double len = Math.sqrt(dx * dx + 1 + dz * dz);
        double lambert = (-dx * sunX + sunY - dz * sunZ) / len;
        return (float) Math.max(0.35, Math.min(1.6, lambert / sunY));
    }

    private static int glow(TopDownScene s, int i, MapStyle style) {
        return ColorMath.grade(s.color[i * 3] * 1.1F, s.color[i * 3 + 1] * 1.1F, s.color[i * 3 + 2] * 1.1F, style);
    }

    private static int lit(TopDownScene s, MapStyle style, int x, int z, int y, double sunX, double sunY, double sunZ,
            double horiz, int maxHeight) {
        int i = s.index(x, z);
        // Crease occlusion: higher neighbours shade where they meet this column.
        int higher = 0;
        for (int[] d : DIRS) {
            if (s.heightAt(x + d[0], z + d[1]) > y) {
                higher++;
            }
        }
        float ao = Math.max(0.5F, 1 - 0.07F * higher);
        float aoSun = Math.max(0, 1 - style.cornerOcclusion() * (1 - ao));

        boolean shadow = inShadow(s, x, z, y, sunX, sunY, sunZ, horiz, maxHeight);
        float direct = shadow ? 0 : style.sunStrength() * aoSun * slopeLight(s, x, z, y, sunX, sunY, sunZ);
        float ambient = style.ambient() * ao * (1 - style.skyOcclusion() * (1 - (0.15F + 0.85F * sky(s, x, z, y))));
        float[] sky = style.shadowTint();
        float[] sun = style.sunTint();
        float f = 1 + style.heightShade() * (float) Math.max(-1, Math.min(1, (y + 1 - 64) / 64.0));
        float lr = (ambient * sky[0] + direct * sun[0]) * f;
        float lg = (ambient * sky[1] + direct * sun[1]) * f;
        float lb = (ambient * sky[2] + direct * sun[2]) * f;

        float r = s.color[i * 3] * lr;
        float g = s.color[i * 3 + 1] * lg;
        float b = s.color[i * 3 + 2] * lb;
        int depth = s.water[i];
        if (depth > 0) {
            // The floor shows through shallow water and fades as it gets deeper.
            float a = Math.min(0.92F, 0.5F + 0.06F * depth);
            r = r * 0.8F * (1 - a) + s.waterColor[i * 3] * lr * a;
            g = g * 0.8F * (1 - a) + s.waterColor[i * 3 + 1] * lg * a;
            b = b * 0.8F * (1 - a) + s.waterColor[i * 3 + 2] * lb * a;
        }
        return ColorMath.grade(r, g, b, style);
    }

    /** Traces from the top of the column towards the sun over the height field. */
    static boolean inShadow(TopDownScene s, int x, int z, int y, double sunX, double sunY, double sunZ, double horiz,
            int maxHeight) {
        if (horiz <= 0) {
            return false;
        }
        double step = 0.5 / horiz;
        double ox = x + 0.5;
        double oz = z + 0.5;
        double oy = y + 1.0;
        for (double t = step; t * horiz <= SHADOW_DISTANCE; t += step) {
            double py = oy + sunY * t;
            if (py > maxHeight + 1) {
                return false;
            }
            int cx = (int) Math.floor(ox + sunX * t);
            int cz = (int) Math.floor(oz + sunZ * t);
            if (cx == x && cz == z) {
                continue;
            }
            int h = s.heightAt(cx, cz);
            if (h != TopDownScene.UNKNOWN && h + 1 > py) {
                return true;
            }
        }
        return false;
    }

    /** Fraction of the sky seen from the column top, from the horizon in eight directions. */
    static float sky(TopDownScene s, int x, int z, int y) {
        float total = 0;
        for (int[] d : DIRS) {
            double len = Math.hypot(d[0], d[1]);
            double maxSlope = 0;
            for (int n = 1; n <= SKY_REACH; n++) {
                int h = s.heightAt(x + d[0] * n, z + d[1] * n);
                if (h != TopDownScene.UNKNOWN && h > y) {
                    maxSlope = Math.max(maxSlope, (h - y) / (n * len));
                }
            }
            total += 1 - (float) (Math.atan(maxSlope) / (Math.PI / 2));
        }
        return total / DIRS.length;
    }

    static int darken(int argb, float f) {
        int r = (int) (((argb >> 16) & 0xFF) * f);
        int g = (int) (((argb >> 8) & 0xFF) * f);
        int b = (int) ((argb & 0xFF) * f);
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }
}
