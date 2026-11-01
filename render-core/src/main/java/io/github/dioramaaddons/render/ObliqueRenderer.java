package io.github.dioramaaddons.render;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws terrain at an angle like the Diorama map: the camera looks north and down at {@code pitch}, so block
 * tops and their south faces show. Map space: {@code u = x}, {@code v = z * sin - y * cos} (south is down,
 * higher blocks move up). Each pixel traces a ray through the blocks in the y-z plane of its column, then
 * the face it hits is lit with the Diorama light model (sun, sky, shadows, occlusion) and outlines are drawn
 * where a neighbouring pixel is much nearer. Pure Java; one instance per tile, any thread.
 */
public final class ObliqueRenderer {
    public static final double DEFAULT_PITCH = 50;
    /** Diorama: longest shadow in blocks, and how much nearer a neighbour must be for an outline. */
    static final double SHADOW_DISTANCE = 40;
    static final float OUTLINE_DEPTH = 1.5F;
    private static final float[] AO_LEVELS = {0.6F, 0.74F, 0.87F, 1.0F};
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    static final int FACE_TOP = 0;
    static final int FACE_SOUTH = 1;

    /** Rendered tile: colours, plus the block hit by each pixel (for coordinates and claim overlays). */
    public record Tile(int size, int[] argb, int[] hitZ, int[] hitY, boolean[] hit) {}

    private final VoxelWorld world;
    private final LookTable looks;
    private final MapStyle style;
    private final double sin;
    private final double cos;
    private final double sunX;
    private final double sunY;
    private final double sunZ;
    private final Map<Long, float[]> lightCache = new HashMap<>();

    public ObliqueRenderer(VoxelWorld world, LookTable looks, MapStyle style, double pitchDegrees) {
        this.world = world;
        this.looks = looks;
        this.style = style;
        this.sin = Math.sin(Math.toRadians(pitchDegrees));
        this.cos = Math.cos(Math.toRadians(pitchDegrees));
        double el = Math.toRadians(style.sunElevation());
        this.sunX = Math.cos(el) * style.sunAzimuthX();
        this.sunY = Math.sin(el);
        this.sunZ = Math.cos(el) * style.sunAzimuthZ();
    }

    public double sin() {
        return sin;
    }

    public double cos() {
        return cos;
    }

    /**
     * @param u0         map u of the tile's left edge
     * @param v0         map v of the tile's top edge
     * @param units      map units across the tile
     * @param pixels     pixels across the tile
     * @param topY       highest block that can be in view (rays start just above it)
     */
    public Tile render(double u0, double v0, double units, int pixels, int topY) {
        int n = pixels * pixels;
        int[] argb = new int[n];
        int[] hitZ = new int[n];
        int[] hitY = new int[n];
        boolean[] hit = new boolean[n];
        float[] depth = new float[n];
        java.util.Arrays.fill(depth, Float.NaN);
        double unitPerPixel = units / pixels;
        for (int px = 0; px < pixels; px++) {
            int x = (int) Math.floor(u0 + (px + 0.5) * unitPerPixel);
            for (int py = 0; py < pixels; py++) {
                double v = v0 + (py + 0.5) * unitPerPixel;
                int i = py * pixels + px;
                trace(x, v, topY, i, argb, hitZ, hitY, hit, depth);
            }
        }
        outline(argb, depth, pixels);
        return new Tile(pixels, argb, hitZ, hitY, hit);
    }

    private void trace(int x, double v, int topY, int i, int[] argb, int[] hitZ, int[] hitY, boolean[] hit, float[] depth) {
        double y0 = topY + 1.0001;
        double z0 = (v + y0 * cos) / sin;
        int iy = (int) Math.floor(y0);
        int iz = (int) Math.floor(z0);
        double tMaxY = (y0 - iy) / sin;
        double tDeltaY = 1 / sin;
        double tMaxZ = (z0 - iz) / cos;
        double tDeltaZ = 1 / cos;
        int face;
        double t;
        int minY = world.minY();
        // Partial cover (plants) and water picked up on the way down.
        float pr = 0, pg = 0, pb = 0, cover = 0;
        float wr = 0, wg = 0, wb = 0;
        int waterBlocks = 0;
        double waterT = -1;
        int waterFace = FACE_TOP, waterY = 0, waterZ = 0;
        for (int steps = 0; steps < 4096; steps++) {
            if (tMaxY < tMaxZ) {
                iy--;
                t = tMaxY;
                tMaxY += tDeltaY;
                face = FACE_TOP;
            } else {
                iz--;
                t = tMaxZ;
                tMaxZ += tDeltaZ;
                face = FACE_SOUTH;
            }
            if (iy < minY) {
                return;
            }
            int surface = world.surface(x, iz);
            if (surface == VoxelWorld.UNKNOWN) {
                if (world.look(x, iy, iz) < 0) {
                    return;
                }
                continue;
            }
            if (iy > surface) {
                continue;
            }
            int id = world.look(x, iy, iz);
            if (id < 0) {
                return;
            }
            if (id == 0) {
                continue;
            }
            byte kind = looks.kind(id);
            if (kind == LookTable.WATER) {
                if (waterBlocks == 0) {
                    waterT = t;
                    waterFace = face;
                    waterY = iy;
                    waterZ = iz;
                    int tint = world.tint(x, iz, LookTable.TINT_WATER);
                    float[] light = light(x, iy, iz, face);
                    wr = surfaceColor(id, face, 0, tint) * light[0];
                    wg = surfaceColor(id, face, 1, tint) * light[1];
                    wb = surfaceColor(id, face, 2, tint) * light[2];
                }
                waterBlocks++;
                if (waterBlocks >= 12) {
                    break;
                }
                continue;
            }
            if (kind == LookTable.PARTIAL) {
                float c = Math.min(1, looks.coverage(id)) * 0.85F * (1 - cover);
                if (c > 0) {
                    int tint = world.tint(x, iz, looks.tint(id));
                    float[] light = light(x, iy, iz, face);
                    pr += surfaceColor(id, face, 0, tint) * light[0] * c;
                    pg += surfaceColor(id, face, 1, tint) * light[1] * c;
                    pb += surfaceColor(id, face, 2, tint) * light[2] * c;
                    cover += c;
                }
                continue;
            }
            // Solid or glowing: the ray stops here.
            float r, g, b;
            int tint = world.tint(x, iz, looks.tint(id));
            if (kind == LookTable.GLOW) {
                r = surfaceColor(id, face, 0, tint) * 1.1F;
                g = surfaceColor(id, face, 1, tint) * 1.1F;
                b = surfaceColor(id, face, 2, tint) * 1.1F;
            } else {
                float[] light = light(x, iy, iz, face);
                r = surfaceColor(id, face, 0, tint) * light[0];
                g = surfaceColor(id, face, 1, tint) * light[1];
                b = surfaceColor(id, face, 2, tint) * light[2];
            }
            r = pr + r * (1 - cover);
            g = pg + g * (1 - cover);
            b = pb + b * (1 - cover);
            if (waterBlocks > 0) {
                float a = Math.min(0.92F, 0.55F + 0.05F * waterBlocks);
                r = r * 0.8F * (1 - a) + wr * a;
                g = g * 0.8F * (1 - a) + wg * a;
                b = b * 0.8F * (1 - a) + wb * a;
                t = waterT;
                iy = waterY;
                iz = waterZ;
            }
            finish(i, r, g, b, t, iy, iz, argb, hitZ, hitY, hit, depth);
            return;
        }
        if (waterBlocks > 0) {
            finish(i, wr, wg, wb, waterT, waterY, waterZ, argb, hitZ, hitY, hit, depth);
        }
    }

    private void finish(int i, float r, float g, float b, double t, int y, int z, int[] argb, int[] hitZ, int[] hitY,
            boolean[] hit, float[] depth) {
        argb[i] = ColorMath.grade(r, g, b, style);
        hitZ[i] = z;
        hitY[i] = y;
        hit[i] = true;
        depth[i] = (float) t;
    }

    private float surfaceColor(int id, int face, int channel, int tint) {
        float base = face == FACE_TOP ? looks.top(id, channel) : looks.side(id, channel);
        if (tint == -1 || (face != FACE_TOP && !looks.sideTinted(id))) {
            return base;
        }
        int c = channel == 0 ? tint >> 16 : channel == 1 ? tint >> 8 : tint;
        return base * ColorMath.toLinear(c & 0xFF);
    }

    /** Light on one face of a block, cached per face. Linear RGB multipliers. */
    float[] light(int x, int y, int z, int face) {
        long key = ((long) x & 0x3FFFFFF) << 38 | ((long) (y + 2048) & 0xFFF) << 26 | ((long) z & 0x1FFFFFF) << 1 | face;
        float[] cached = lightCache.get(key);
        if (cached != null) {
            return cached;
        }
        boolean top = face == FACE_TOP;
        double ox = x + 0.5;
        double oy = top ? y + 1.001 : y + 0.5;
        double oz = top ? z + 0.5 : z + 1.001;
        float ao = top ? aoTop(x, y + 1, z) : aoSouth(x, y, z + 1);
        float aoSun = Math.max(0, 1 - style.cornerOcclusion() * (1 - ao));
        double lambert = top ? 1 : Math.max(0, sunZ) / sunY;
        float direct = 0;
        if (lambert > 0 && !inShadow(ox, oy, oz)) {
            direct = (float) (style.sunStrength() * lambert) * aoSun;
        }
        float sky = top ? sky(x, z, y + 1) : sky(x, z + 1, y) * 0.6F;
        float ambient = style.ambient() * ao * (1 - style.skyOcclusion() * (1 - (0.15F + 0.85F * sky)));
        float[] shadowTint = style.shadowTint();
        float[] sunTint = style.sunTint();
        float f = 1 + style.heightShade() * (float) Math.max(-1, Math.min(1, (oy - 64) / 64));
        float[] light = {
            (ambient * shadowTint[0] + direct * sunTint[0]) * f,
            (ambient * shadowTint[1] + direct * sunTint[1]) * f,
            (ambient * shadowTint[2] + direct * sunTint[2]) * f
        };
        lightCache.put(key, light);
        return light;
    }

    private boolean opaqueAt(int x, int y, int z) {
        int s = world.surface(x, z);
        if (s == VoxelWorld.UNKNOWN || y > s) {
            return false;
        }
        int id = world.look(x, y, z);
        return id > 0 && looks.opaque(id);
    }

    private float aoTop(int x, int y, int z) {
        int n = 0;
        for (int[] d : DIRS) {
            if (opaqueAt(x + d[0], y, z + d[1])) {
                n++;
            }
        }
        return AO_LEVELS[Math.max(0, 3 - Math.min(3, (n + 1) / 2))];
    }

    private float aoSouth(int x, int y, int z) {
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if ((dx != 0 || dy != 0) && opaqueAt(x + dx, y + dy, z)) {
                    n++;
                }
            }
        }
        return AO_LEVELS[Math.max(0, 3 - Math.min(3, (n + 1) / 2))];
    }

    /** Sky seen from a point, from the horizon of the surface in eight directions. */
    private float sky(int x, int z, int y) {
        float total = 0;
        for (int[] d : DIRS) {
            double len = Math.hypot(d[0], d[1]);
            double maxSlope = 0;
            for (int s = 1; s <= 8; s++) {
                int h = world.surface(x + d[0] * s, z + d[1] * s);
                if (h != VoxelWorld.UNKNOWN && h + 1 > y) {
                    maxSlope = Math.max(maxSlope, (h + 1 - y) / (s * len));
                }
            }
            total += 1 - (float) (Math.atan(maxSlope) / (Math.PI / 2));
        }
        return total / DIRS.length;
    }

    /** Steps towards the sun through the blocks. */
    boolean inShadow(double ox, double oy, double oz) {
        int maxY = world.maxY();
        double step = 0.35;
        int lx = Integer.MIN_VALUE, ly = 0, lz = 0;
        for (double t = step; t <= SHADOW_DISTANCE; t += step) {
            double y = oy + sunY * t;
            if (y > maxY) {
                return false;
            }
            int bx = (int) Math.floor(ox + sunX * t);
            int by = (int) Math.floor(y);
            int bz = (int) Math.floor(oz + sunZ * t);
            if (bx == lx && by == ly && bz == lz) {
                continue;
            }
            lx = bx;
            ly = by;
            lz = bz;
            if (opaqueAt(bx, by, bz)) {
                return true;
            }
        }
        return false;
    }

    /** Darkens pixels whose neighbour is much nearer: the silhouettes of the Diorama look. */
    private void outline(int[] argb, float[] depth, int size) {
        float keep = 1 - style.outline();
        int[] copy = argb.clone();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                float d = depth[i];
                if (Float.isNaN(d)) {
                    continue;
                }
                if (nearer(depth, x - 1, y, size, d) || nearer(depth, x + 1, y, size, d)
                        || nearer(depth, x, y - 1, size, d) || nearer(depth, x, y + 1, size, d)) {
                    argb[i] = TopDownShader.darken(copy[i], keep);
                }
            }
        }
    }

    private static boolean nearer(float[] depth, int x, int y, int size, float here) {
        if (x < 0 || y < 0 || x >= size || y >= size) {
            return false;
        }
        float d = depth[y * size + x];
        return !Float.isNaN(d) && d < here - OUTLINE_DEPTH;
    }
}
