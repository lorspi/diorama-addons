package io.github.dioramaaddons.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ObliqueRendererTest {
    /** Flat grass at y=64 over 64x64 blocks, with an optional stone tower. */
    static final class TestWorld implements VoxelWorld {
        final int grass;
        final int stone;
        final int towerX, towerZ, towerTop;

        TestWorld(LookTable looks, int towerX, int towerZ, int towerTop) {
            grass = looks.add(LookTable.SOLID, LookTable.TINT_NONE, 1, new float[] {0.1F, 0.4F, 0.05F}, new float[] {0.3F, 0.2F, 0.1F});
            stone = looks.add(LookTable.SOLID, LookTable.TINT_NONE, 1, new float[] {0.3F, 0.3F, 0.3F}, new float[] {0.25F, 0.25F, 0.25F});
            this.towerX = towerX;
            this.towerZ = towerZ;
            this.towerTop = towerTop;
        }

        boolean inside(int x, int z) {
            return x >= 0 && z >= 0 && x < 64 && z < 64;
        }

        boolean tower(int x, int z) {
            return towerTop > 64 && x >= towerX && x < towerX + 3 && z >= towerZ && z < towerZ + 3;
        }

        @Override
        public int minY() {
            return 0;
        }

        @Override
        public int maxY() {
            return 128;
        }

        @Override
        public int look(int x, int y, int z) {
            if (!inside(x, z)) {
                return -1;
            }
            if (tower(x, z) && y <= towerTop) {
                return stone;
            }
            return y <= 64 ? grass : 0;
        }

        @Override
        public int surface(int x, int z) {
            if (!inside(x, z)) {
                return UNKNOWN;
            }
            return tower(x, z) ? towerTop : 64;
        }

        @Override
        public int tint(int x, int z, byte kind) {
            return -1;
        }
    }

    static double v(double z, double y, ObliqueRenderer r) {
        return z * r.sin() - y * r.cos();
    }

    @Test
    void flatGroundIsHitAtTheRightBlock() {
        LookTable looks = new LookTable();
        TestWorld w = new TestWorld(looks, 0, 0, 0);
        ObliqueRenderer r = new ObliqueRenderer(w, looks, MapStyle.DIORAMA, ObliqueRenderer.DEFAULT_PITCH);
        // A tile whose centre shows the top of block (32, 64, 32).
        double v0 = v(32.5, 65, r) - 8;
        ObliqueRenderer.Tile t = r.render(24, v0, 16, 64, 100);
        int i = 32 * 64 + 32;
        assertTrue(t.hit()[i]);
        assertEquals(32, t.hitZ()[i]);
        assertEquals(64, t.hitY()[i]);
        assertEquals(0xFF, t.argb()[i] >>> 24);
    }

    @Test
    void aTowerStandsUpAndCastsAShadowEast() {
        LookTable looks = new LookTable();
        TestWorld w = new TestWorld(looks, 30, 30, 80);
        ObliqueRenderer r = new ObliqueRenderer(w, looks, MapStyle.DIORAMA, ObliqueRenderer.DEFAULT_PITCH);
        // Above the tower's ground position on screen we now see the tower, not the ground behind it.
        double v0 = v(31.5, 65, r) - 24;
        ObliqueRenderer.Tile t = r.render(23, v0, 32, 64, 100);
        boolean sawStoneHigh = false;
        for (int i = 0; i < t.hit().length; i++) {
            if (t.hit()[i] && t.hitY()[i] > 64) {
                sawStoneHigh = true;
                break;
            }
        }
        assertTrue(sawStoneHigh, "the tower should be visible");
        // Sun in the west: ground just east of the tower is shaded, ground west of it is not.
        assertTrue(r.inShadow(34.5, 65.001, 31.5));
        assertFalse(r.inShadow(27.5, 65.001, 31.5));
    }

    @Test
    void outsideTheWorldIsTransparent() {
        LookTable looks = new LookTable();
        TestWorld w = new TestWorld(looks, 0, 0, 0);
        ObliqueRenderer r = new ObliqueRenderer(w, looks, MapStyle.DIORAMA, ObliqueRenderer.DEFAULT_PITCH);
        ObliqueRenderer.Tile t = r.render(500, 500, 16, 16, 100);
        for (int p : t.argb()) {
            assertEquals(0, p);
        }
    }

    @Test
    void southFacesAreDarkerThanTops() {
        LookTable looks = new LookTable();
        TestWorld w = new TestWorld(looks, 30, 30, 80);
        ObliqueRenderer r = new ObliqueRenderer(w, looks, MapStyle.DIORAMA, ObliqueRenderer.DEFAULT_PITCH);
        float[] top = r.light(31, 80, 31, ObliqueRenderer.FACE_TOP);
        float[] south = r.light(31, 70, 32, ObliqueRenderer.FACE_SOUTH);
        assertTrue(top[1] > south[1]);
    }
}
