package net.weaver.minimap.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceShadingTest
{
    private static final double[] SHADOW = {0.518D, 0.678D, 1.0D};
    private static final double[] OUT = new double[3];

    @Test
    void flatGroundStaysBrightAndNeutral()
    {
        SurfaceShading.light(0, 0, SHADOW[0], SHADOW[1], SHADOW[2], OUT);
        assertEquals(0.9436D, OUT[0], 0.01D);
        assertTrue(OUT[1] > OUT[0], "the blue shadow channel must stay the brightest");
        assertTrue(OUT[2] >= OUT[1]);
    }

    @Test
    void northWestFacesAreBrighterThanSouthEastOnes()
    {
        double[] lit = new double[3];
        double[] dark = new double[3];
        SurfaceShading.light(2, 2, SHADOW[0], SHADOW[1], SHADOW[2], lit);
        SurfaceShading.light(-2, -2, SHADOW[0], SHADOW[1], SHADOW[2], dark);
        assertTrue(lit[1] > dark[1]);
        assertTrue(lit[1] <= 1.3D);
        assertTrue(dark[1] >= 0.45D);
    }

    @Test
    void steepSlopeKeepsBothExtremesInsideARenderableRange()
    {
        SurfaceShading.light(24, 24, SHADOW[0], SHADOW[1], SHADOW[2], OUT);
        assertTrue(OUT[0] <= 1.2D);
        SurfaceShading.light(-24, -24, SHADOW[0], SHADOW[1], SHADOW[2], OUT);
        assertTrue(OUT[0] >= 0.4D);
    }

    @Test
    void fullDaylightIsFullBrightness()
    {
        assertEquals(1.0D, SurfaceShading.brightness(15, 0, 1.0D), 0.0001D);
    }

    @Test
    void nightKeepsAThirdOfTheLight()
    {
        assertEquals(9.0D / 24.0D, SurfaceShading.brightness(15, 0, 0.0D), 0.0001D);
    }

    @Test
    void blockLightLiftsACoveredColumn()
    {
        assertTrue(SurfaceShading.brightness(0, 15, 0.0D)
                > SurfaceShading.brightness(0, 0, 0.0D));
    }

    @Test
    void depthDarkensLowGroundAndClamps()
    {
        assertEquals(1.0D, SurfaceShading.depth(63, 63), 0.0001D);
        assertEquals(SurfaceShading.MAX_DEPTH, SurfaceShading.depth(300, 63), 0.0001D);
        assertEquals(SurfaceShading.MIN_DEPTH, SurfaceShading.depth(-64, 63), 0.0001D);
        assertTrue(SurfaceShading.depth(40, 63) < 1.0D);
    }

    @Test
    void scaleKeepsTheColourOpaque()
    {
        int color = SurfaceShading.scale(0xFF336699, 1.0D, 1.0D, 1.0D);
        assertEquals(0xFF336699, color);
        int dark = SurfaceShading.scale(0xFF336699, 0.0D, 0.0D, 0.0D);
        assertEquals(0xFF000000, dark);
    }
}
