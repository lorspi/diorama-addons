package io.github.dioramaaddons.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TopDownShaderTest {
    static TopDownScene flat(int size, int y) {
        TopDownScene s = new TopDownScene(size, size);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                s.set(x, z, y, 0.2F, 0.4F, 0.1F);
            }
        }
        return s;
    }

    static int brightness(int argb) {
        return ((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF);
    }

    @Test
    void colourConversionsRoundTrip() {
        for (int i = 0; i < 256; i += 17) {
            assertEquals(i, ColorMath.toSrgb(ColorMath.toLinear(i)), 1);
        }
    }

    @Test
    void flatGroundIsEvenAndOpaque() {
        int[] px = TopDownShader.shade(flat(16, 64), MapStyle.DIORAMA, 1);
        int centre = px[8 + 8 * 16];
        assertEquals(0xFF, centre >>> 24);
        assertEquals(centre, px[9 + 8 * 16]);
        assertEquals(centre, px[8 + 9 * 16]);
    }

    @Test
    void unknownColumnsAreTransparent() {
        TopDownScene s = new TopDownScene(4, 4);
        for (int p : TopDownShader.shade(s, MapStyle.DIORAMA, 2)) {
            assertEquals(0, p);
        }
    }

    @Test
    void aWallCastsAShadowAwayFromTheSun() {
        // The sun is to the west (-x): a wall at x = 10 shades the ground east of it.
        TopDownScene s = flat(32, 64);
        for (int z = 0; z < 32; z++) {
            s.set(10, z, 80, 0.3F, 0.3F, 0.3F);
        }
        int[] px = TopDownShader.shade(s, MapStyle.DIORAMA, 1);
        int shaded = px[13 + 16 * 32];
        int lit = px[5 + 16 * 32];
        assertTrue(brightness(shaded) < brightness(lit) * 0.8, "east of the wall should be in shadow");
        double el = Math.toRadians(MapStyle.DIORAMA.sunElevation());
        assertTrue(TopDownShader.inShadow(s, 13, 16, 64, Math.cos(el) * -0.878, Math.sin(el), Math.cos(el) * 0.479,
                Math.cos(el), 80));
        assertFalse(TopDownShader.inShadow(s, 5, 16, 64, Math.cos(el) * -0.878, Math.sin(el), Math.cos(el) * 0.479,
                Math.cos(el), 80));
    }

    @Test
    void outlinesMarkTheFootOfHigherGround() {
        TopDownScene s = flat(8, 64);
        s.set(4, 4, 70, 0.2F, 0.4F, 0.1F);
        int[] px = TopDownShader.shade(s, MapStyle.DIORAMA, 4);
        int w = 8 * 4;
        // Column (3,4): its east edge pixel touches the higher column, its west edge does not.
        int eastEdge = px[(4 * 4 + 1) * w + 3 * 4 + 3];
        int inside = px[(4 * 4 + 1) * w + 3 * 4 + 1];
        assertTrue(brightness(eastEdge) < brightness(inside));
    }

    @Test
    void slopesFacingTheSunAreBrighter() {
        // The sun is to the west: ground rising towards the east faces west, towards the sun.
        TopDownScene s = new TopDownScene(8, 8);
        for (int z = 0; z < 8; z++) {
            for (int x = 0; x < 8; x++) {
                s.set(x, z, 64 + x, 0.2F, 0.4F, 0.1F);
            }
        }
        double el = Math.toRadians(MapStyle.DIORAMA.sunElevation());
        float facing = TopDownShader.slopeLight(s, 4, 4, 68, Math.cos(el) * -0.878, Math.sin(el), Math.cos(el) * 0.479);
        TopDownScene flat = flat(8, 64);
        float level = TopDownShader.slopeLight(flat, 4, 4, 64, Math.cos(el) * -0.878, Math.sin(el), Math.cos(el) * 0.479);
        assertEquals(1F, level, 1e-6);
        assertTrue(facing > level, "a slope facing the western sun is brighter");
        float away = TopDownShader.slopeLight(s, 4, 4, 68, Math.cos(el) * 0.878, Math.sin(el), Math.cos(el) * 0.479);
        assertTrue(away < level, "the same slope under an eastern sun is darker");
    }

    @Test
    void skyIsOpenOnFlatGroundAndClosedInAPit() {
        TopDownScene s = flat(17, 64);
        assertEquals(1F, TopDownShader.sky(s, 8, 8, 64), 1e-6);
        for (int z = 0; z < 17; z++) {
            for (int x = 0; x < 17; x++) {
                if (x != 8 || z != 8) {
                    s.set(x, z, 90, 0.2F, 0.4F, 0.1F);
                }
            }
        }
        assertTrue(TopDownShader.sky(s, 8, 8, 64) < 0.3F);
    }

    @Test
    void shallowWaterShowsTheFloorMoreThanDeepWater() {
        TopDownScene shallow = flat(4, 62);
        TopDownScene deep = flat(4, 62);
        for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 4; x++) {
                shallow.setWater(x, z, 1, 0.02F, 0.05F, 0.4F);
                deep.setWater(x, z, 10, 0.02F, 0.05F, 0.4F);
            }
        }
        int s = TopDownShader.shade(shallow, MapStyle.DIORAMA, 1)[5];
        int d = TopDownShader.shade(deep, MapStyle.DIORAMA, 1)[5];
        assertTrue(((s >> 8) & 0xFF) > ((d >> 8) & 0xFF), "green floor shows more through shallow water");
    }
}
