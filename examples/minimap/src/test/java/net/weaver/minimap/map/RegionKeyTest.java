package net.weaver.minimap.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionKeyTest
{
    @Test
    void positiveBlocksMapToRegions()
    {
        assertEquals(new RegionKey("minecraft:overworld", 0, 0),
                RegionKey.ofBlock("minecraft:overworld", 0, 0));
        assertEquals(new RegionKey("minecraft:overworld", 0, 0),
                RegionKey.ofBlock("minecraft:overworld", 255, 255));
        assertEquals(new RegionKey("minecraft:overworld", 1, 1),
                RegionKey.ofBlock("minecraft:overworld", 256, 256));
        assertEquals(new RegionKey("minecraft:overworld", 4, 9),
                RegionKey.ofBlock("minecraft:overworld", 1024, 2304));
    }

    @Test
    void negativeBlocksUseFloorDivision()
    {
        assertEquals(new RegionKey("minecraft:the_nether", -1, -1),
                RegionKey.ofBlock("minecraft:the_nether", -1, -1));
        assertEquals(new RegionKey("minecraft:the_nether", -1, -1),
                RegionKey.ofBlock("minecraft:the_nether", -256, -256));
        assertEquals(new RegionKey("minecraft:the_nether", -2, 1),
                RegionKey.ofBlock("minecraft:the_nether", -257, 300));
    }

    @Test
    void cornerAndFileNameAccessorsUseRegionCoordinates()
    {
        RegionKey key = new RegionKey("minecraft:overworld", 3, -7);

        assertEquals(768, key.minBlockX());
        assertEquals(-1792, key.minBlockZ());
        assertEquals("r.3.-7.wmap", key.fileName());
        assertEquals("minecraft:overworld", key.dimension());
        assertEquals(3, key.regionX());
        assertEquals(-7, key.regionZ());
    }

    @Test
    void blankDimensionIsRejected()
    {
        assertThrows(NullPointerException.class, () -> new RegionKey(null, 0, 0));
        IllegalArgumentException blank = assertThrows(IllegalArgumentException.class,
                () -> new RegionKey("   ", 0, 0));
        assertTrue(blank.getMessage().contains("dimension"));
        assertThrows(IllegalArgumentException.class,
                () -> RegionKey.ofBlock("", 0, 0));
    }
}
