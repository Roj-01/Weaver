package net.weaver.minimap.map;

/**
 * Mutable counters owned by the client thread and published as {@link MapStats}.
 *
 * <p>Increment methods avoid allocation; {@link #snapshot} is the only method
 * that allocates and is called when the UI asks for statistics.</p>
 */
final class MapCounters
{
    private long scannedChunks;
    private long regionHits;
    private long regionMisses;
    private long caveSlicesScanned;
    private long caveHits;
    private long caveMisses;

    void reset()
    {
        scannedChunks = 0L;
        regionHits = 0L;
        regionMisses = 0L;
        caveSlicesScanned = 0L;
        caveHits = 0L;
        caveMisses = 0L;
    }

    void scannedChunk()
    {
        scannedChunks++;
    }

    void regionHit()
    {
        regionHits++;
    }

    void regionMiss()
    {
        regionMisses++;
    }

    void caveSlicesScanned()
    {
        caveSlicesScanned++;
    }

    void caveHit()
    {
        caveHits++;
    }

    void caveMiss()
    {
        caveMisses++;
    }

    long caveScannedCount()
    {
        return caveSlicesScanned;
    }

    MapStats snapshot(int loadedRegions, int queuedRegionLoads, int loadedCaveSlices,
                      int queuedCaveLoads, int scanQueueDepth)
    {
        return new MapStats(scannedChunks, loadedRegions, queuedRegionLoads,
                regionHits, regionMisses, caveSlicesScanned, loadedCaveSlices,
                queuedCaveLoads, caveHits, caveMisses, scanQueueDepth);
    }
}
