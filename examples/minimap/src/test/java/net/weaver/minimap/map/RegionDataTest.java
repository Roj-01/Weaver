package net.weaver.minimap.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionDataTest
{
    private static final RegionKey KEY = new RegionKey("minecraft:overworld", 0, 0);

    @Test
    void constructorInitializesEmptyRegion()
    {
        RegionData data = new RegionData(KEY);

        assertEquals(16, RegionData.CHUNKS_PER_REGION);
        assertEquals(256, RegionData.BLOCKS_PER_REGION);
        assertEquals(16, RegionData.CHUNK_PIXELS);
        assertSame(KEY, data.key());
        assertEquals(256 * 256, data.colors().length);
        assertEquals(256 * 256, data.heights().length);
        assertEquals(256, data.present().length);
        assertTrue(data.isEmpty());
        assertFalse(data.isDirty());
        for (boolean chunkPresent : data.present())
        {
            assertFalse(chunkPresent);
        }
        assertThrows(NullPointerException.class, () -> new RegionData(null));
    }

    @Test
    void colorAndHeightRoundTrip()
    {
        RegionData data = new RegionData(KEY);

        data.setColor(0, 0, 0xFF112233);
        data.setColor(255, 255, 0x80112233);
        data.setColor(17, 200, -1);
        data.setHeight(0, 0, (short) -64);
        data.setHeight(255, 255, (short) 320);
        data.setHeight(17, 200, (short) 127);

        assertEquals(0xFF112233, data.colorAt(0, 0));
        assertEquals(0x80112233, data.colorAt(255, 255));
        assertEquals(-1, data.colorAt(17, 200));
        assertEquals((short) -64, data.heightAt(0, 0));
        assertEquals((short) 320, data.heightAt(255, 255));
        assertEquals((short) 127, data.heightAt(17, 200));
        assertEquals(0xFF112233, data.colors()[0]);
        assertEquals(0x80112233, data.colors()[255 * 256 + 255]);
        assertEquals((short) -64, data.heights()[0]);
    }

    @Test
    void blockCoordinatesAreBoundsChecked()
    {
        RegionData data = new RegionData(KEY);

        assertThrows(IndexOutOfBoundsException.class, () -> data.colorAt(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> data.colorAt(0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> data.colorAt(256, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> data.colorAt(0, 256));
        assertThrows(IndexOutOfBoundsException.class, () -> data.setColor(256, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> data.heightAt(0, 256));
        assertThrows(IndexOutOfBoundsException.class, () -> data.setHeight(-1, 0, (short) 0));

        IndexOutOfBoundsException error = assertThrows(IndexOutOfBoundsException.class,
                () -> data.colorAt(256, 0));
        assertTrue(error.getMessage().contains("localX"));
    }

    @Test
    void chunkCoordinatesAreBoundsChecked()
    {
        RegionData data = new RegionData(KEY);

        assertThrows(IndexOutOfBoundsException.class, () -> data.isChunkPresent(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> data.isChunkPresent(0, 16));
        assertThrows(IndexOutOfBoundsException.class,
                () -> data.setChunkPresent(16, 0, true));
        assertThrows(IndexOutOfBoundsException.class,
                () -> data.setChunkPresent(0, -1, true));

        IndexOutOfBoundsException error = assertThrows(IndexOutOfBoundsException.class,
                () -> data.isChunkPresent(0, 16));
        assertTrue(error.getMessage().contains("localChunkZ"));
    }

    @Test
    void presenceAndDirtyFlagsTrackChanges()
    {
        RegionData data = new RegionData(KEY);

        assertFalse(data.isDirty());
        data.markDirty();
        assertTrue(data.isDirty());
        data.clearDirty();
        assertFalse(data.isDirty());

        assertTrue(data.isEmpty());
        data.setChunkPresent(0, 0, true);
        assertTrue(data.isChunkPresent(0, 0));
        assertFalse(data.isEmpty());
        data.setChunkPresent(0, 0, false);
        assertTrue(data.isEmpty());

        data.setChunkPresent(15, 15, true);
        assertTrue(data.isChunkPresent(15, 15));
        assertTrue(data.present()[15 * 16 + 15]);
        assertFalse(data.isEmpty());
    }

    @Test
    void copyColorsIsDefensive()
    {
        RegionData data = new RegionData(KEY);
        data.setColor(4, 5, 0xFF00FF00);

        int[] copy = data.copyColors();
        assertNotSame(data.colors(), copy);
        assertEquals(0xFF00FF00, copy[5 * 256 + 4]);

        copy[5 * 256 + 4] = 0;
        assertEquals(0xFF00FF00, data.colorAt(4, 5));
    }

    @Test
    void backingArraysAreExposedDirectly()
    {
        RegionData data = new RegionData(KEY);

        data.colors()[0] = 7;
        data.heights()[0] = 9;
        data.present()[0] = true;

        assertEquals(7, data.colorAt(0, 0));
        assertEquals((short) 9, data.heightAt(0, 0));
        assertTrue(data.isChunkPresent(0, 0));
    }

    @Test
    void mipAveragesFourByFourSurfacePixels()
    {
        RegionData data = new RegionData(KEY);
        data.setColor(0, 0, 0xFF000000);
        data.setColor(1, 0, 0xFF000000);
        data.setColor(2, 0, 0xFF000000);
        data.setColor(3, 0, 0xFF000000);
        for (int x = 0; x < 256; x++)
        {
            for (int z = 0; z < 256; z++)
            {
                if (x < 4 && z < 4)
                {
                    continue;
                }
                data.setColor(x, z, 0xFF0A141E);
            }
        }

        assertEquals(64, RegionData.MIP_SIZE);
        assertEquals(4, RegionData.MIP_SCALE);
        assertEquals(64 * 64, data.mip64().length);
        assertEquals(0xFF000000, data.mipColorAt(0, 0));
        assertEquals(0xFF0A141E, data.mipColorAt(1, 1));
        assertFalse(data.isMipDirty());

        data.setColor(0, 0, 0xFFFFFFFF);
        assertTrue(data.isMipDirty());
        assertEquals(0xFF404040, data.mipColorAt(0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> data.mipColorAt(64, 0));
    }

    @Test
    void mipIgnoresTransparentPixels()
    {
        RegionData data = new RegionData(KEY);
        data.setColor(0, 0, 0xFF204060);

        assertEquals(0xFF204060, data.mipColorAt(0, 0));
        assertEquals(0, data.mipColorAt(1, 0));
    }

    @Test
    void biomePaletteIsCompactAndDeduplicated()
    {
        RegionData data = new RegionData(KEY);

        assertEquals(1, data.biomePaletteSize());
        assertEquals(RegionData.UNKNOWN_BIOME, data.biomeId(0));
        int plains = data.biomeIndex("minecraft:plains");
        int desert = data.biomeIndex("minecraft:desert");

        assertEquals(1, plains);
        assertEquals(2, desert);
        assertEquals(plains, data.biomeIndex("minecraft:plains"));
        data.setBiomeIndex(7, 8, desert);
        assertEquals(desert, data.biomeIndexAt(7, 8));
        assertEquals("minecraft:desert", data.biomeAt(7, 8));
        assertEquals(0, data.biomeIndexAt(0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> data.setBiomeIndex(0, 0, 3));
    }

    @Test
    void copyIncludesMipAndBiomes()
    {
        RegionData data = new RegionData(KEY);
        data.setColor(10, 11, 0xFF123456);
        data.setBiomeIndex(10, 11, data.biomeIndex("minecraft:plains"));
        data.setMetadataVersion(5);
        data.mip64();
        data.markDirty();

        RegionData copy = data.copy();

        assertNotSame(data, copy);
        assertEquals(0xFF123456, copy.colorAt(10, 11));
        assertEquals("minecraft:plains", copy.biomeAt(10, 11));
        assertEquals(5, copy.metadataVersion());
        assertEquals(data.mipColorAt(0, 0), copy.mipColorAt(0, 0));
        assertFalse(copy.isDirty());
        copy.setColor(10, 11, 0);
        assertEquals(0xFF123456, data.colorAt(10, 11));
    }
}
