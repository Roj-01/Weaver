package net.weaver.minimap.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaveSliceScannerTest
{
    private static final RegionKey KEY = new RegionKey("minecraft:overworld", 0, 0);

    @Test
    void airIsTransparentAndSolidsArePresent()
    {
        FakeCaveBlockSource source = new FakeCaveBlockSource();
        source.airFrom = 20;
        source.airTo = 30;

        CaveSlice slice = CaveSliceScanner.scan(source, KEY, 25, CavePalette.CAVE);

        assertEquals(KEY, slice.key());
        assertEquals(25, slice.y());
        assertFalse(slice.isPresent(0, 0));
        assertEquals(0, slice.colorAt(0, 0));
        assertFalse(slice.isPresent(128, 128));
    }

    @Test
    void floorIsBrighterThanCeiling()
    {
        FakeCaveBlockSource source = new FakeCaveBlockSource();
        source.airFrom = 20;
        source.airTo = 30;

        CaveSlice floor = CaveSliceScanner.scan(source, KEY, 19, CavePalette.CAVE);
        CaveSlice ceiling = CaveSliceScanner.scan(source, KEY, 31, CavePalette.CAVE);

        assertTrue(floor.isPresent(10, 10));
        assertTrue(ceiling.isPresent(10, 10));
        assertTrue(luminance(floor.colorAt(10, 10)) > luminance(ceiling.colorAt(10, 10)));
    }

    @Test
    void waterAndLavaAreColored()
    {
        FakeCaveBlockSource source = new FakeCaveBlockSource();
        source.waterFrom = 5;
        source.waterTo = 5;
        source.lavaFrom = 6;
        source.lavaTo = 6;

        CaveSlice water = CaveSliceScanner.scan(source, KEY, 5, CavePalette.CAVE);
        CaveSlice lava = CaveSliceScanner.scan(source, KEY, 6, CavePalette.CAVE);

        assertTrue(water.isPresent(1, 1));
        assertTrue(lava.isPresent(1, 1));
        assertTrue(blue(water.colorAt(1, 1)) > red(water.colorAt(1, 1)));
        assertTrue(red(lava.colorAt(1, 1)) > blue(lava.colorAt(1, 1)));
    }

    @Test
    void voidColumnsAreDarkAndPresent()
    {
        FakeCaveBlockSource source = new FakeCaveBlockSource();

        CaveSlice slice = CaveSliceScanner.scan(source, KEY, -100, CavePalette.CAVE);

        assertTrue(slice.isPresent(0, 0));
        assertTrue(luminance(slice.colorAt(0, 0)) < 64);
    }

    @Test
    void unloadedColumnsAreTransparent()
    {
        FakeCaveBlockSource source = new FakeCaveBlockSource();
        source.unloaded = true;

        CaveSlice slice = CaveSliceScanner.scan(source, KEY, 20, CavePalette.CAVE);

        assertTrue(slice.isEmpty());
    }

    @Test
    void dimensionPalettesDiffer()
    {
        FakeCaveBlockSource source = new FakeCaveBlockSource();

        CaveSlice cave = CaveSliceScanner.scan(source, KEY, 40, CavePalette.CAVE);
        CaveSlice nether = CaveSliceScanner.scan(source, KEY, 40, CavePalette.NETHER);
        CaveSlice end = CaveSliceScanner.scan(source, KEY, 40, CavePalette.END);

        assertEquals(CavePalette.NETHER, CavePalette.forDimension("minecraft:the_nether"));
        assertEquals(CavePalette.END, CavePalette.forDimension("minecraft:the_end"));
        assertEquals(CavePalette.CAVE, CavePalette.forDimension("minecraft:overworld"));
        assertEquals(CavePalette.CAVE, CavePalette.forDimension(null));
        assertNotEquals(cave.colorAt(3, 3), nether.colorAt(3, 3));
        assertNotEquals(cave.colorAt(3, 3), end.colorAt(3, 3));
    }

    @Test
    void scanningIsDeterministicAcrossIncrementalBudgets()
    {
        FakeCaveBlockSource source = new FakeCaveBlockSource();
        source.airFrom = 20;
        source.airTo = 30;
        CaveSlice direct = CaveSliceScanner.scan(source, KEY, 20, CavePalette.CAVE);

        CaveSliceBuild build = new CaveSliceBuild(source, KEY, 20, CavePalette.CAVE);
        int steps = 0;
        while (!build.step(1))
        {
            steps++;
            assertTrue(steps < 300);
        }

        assertArrayEquals(direct.colors(), build.slice().colors());
        assertArrayEquals(direct.present(), build.slice().present());
        assertEquals(256, build.progress());
    }

    @Test
    void scanUsesRegionOrigin()
    {
        FakeCaveBlockSource source = new FakeCaveBlockSource();
        RegionKey key = new RegionKey("minecraft:overworld", -1, 2);

        CaveSlice slice = CaveSliceScanner.scan(source, key, 40, CavePalette.CAVE);

        assertEquals(-256, key.minBlockX());
        assertEquals(512, key.minBlockZ());
        assertTrue(slice.isPresent(0, 0));
    }

    private static int luminance(int argb)
    {
        return (((argb >> 16) & 0xFF) * 30 + ((argb >> 8) & 0xFF) * 59
                + (argb & 0xFF) * 11) / 100;
    }

    private static int red(int argb)
    {
        return (argb >> 16) & 0xFF;
    }

    private static int blue(int argb)
    {
        return argb & 0xFF;
    }
}
