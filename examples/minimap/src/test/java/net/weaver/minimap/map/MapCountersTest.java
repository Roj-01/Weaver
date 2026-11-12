package net.weaver.minimap.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapCountersTest
{
    @Test
    void snapshotReflectsEveryCounter()
    {
        MapCounters counters = new MapCounters();
        counters.scannedChunk();
        counters.scannedChunk();
        counters.regionHit();
        counters.regionMiss();
        counters.regionMiss();
        counters.caveSlicesScanned();
        counters.caveHit();
        counters.caveHit();
        counters.caveHit();
        counters.caveMiss();

        MapStats stats = counters.snapshot(7, 3, 4, 2, 11);

        assertEquals(2L, stats.scannedChunks());
        assertEquals(7, stats.loadedRegions());
        assertEquals(3, stats.queuedRegionLoads());
        assertEquals(1L, stats.regionCacheHits());
        assertEquals(2L, stats.regionCacheMisses());
        assertEquals(1L, stats.caveSlicesScanned());
        assertEquals(4, stats.loadedCaveSlices());
        assertEquals(2, stats.queuedCaveLoads());
        assertEquals(3L, stats.caveCacheHits());
        assertEquals(1L, stats.caveCacheMisses());
        assertEquals(11, stats.scanQueueDepth());
    }

    @Test
    void resetClearsAllCounters()
    {
        MapCounters counters = new MapCounters();
        counters.scannedChunk();
        counters.regionHit();
        counters.caveMiss();

        counters.reset();
        MapStats stats = counters.snapshot(0, 0, 0, 0, 0);

        assertEquals(MapStats.empty(), stats);
    }
}
