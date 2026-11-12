package net.weaver.minimap.map;

/**
 * Immutable counters describing map engine work.
 *
 * <p>The snapshot is cheap to hold for a frame but is rebuilt after every
 * client tick. Rendering code should call {@link MapManager#stats()} once per
 * frame instead of polling individual counters.</p>
 *
 * @param scannedChunks     surface chunks scanned since the world joined
 * @param loadedRegions     surface regions currently in memory
 * @param queuedRegionLoads surface regions waiting for or running a disk load
 * @param regionCacheHits   successful in-memory surface lookups
 * @param regionCacheMisses surface lookups that had to reach the cache miss path
 * @param caveSlicesScanned cave slices produced by the scanner
 * @param loadedCaveSlices  cave slices currently in memory
 * @param queuedCaveLoads   cave slices waiting for or running a disk load
 * @param caveCacheHits     successful in-memory cave lookups
 * @param caveCacheMisses   cave lookups that missed
 * @param scanQueueDepth    surface chunks waiting in the scan queue
 */
public record MapStats(long scannedChunks, int loadedRegions, int queuedRegionLoads,
                       long regionCacheHits, long regionCacheMisses,
                       long caveSlicesScanned, int loadedCaveSlices, int queuedCaveLoads,
                       long caveCacheHits, long caveCacheMisses, int scanQueueDepth)
{
    /**
     * Returns an all-zero snapshot.
     *
     * @return an empty snapshot, never {@code null}
     */
    public static MapStats empty()
    {
        return new MapStats(0L, 0, 0, 0L, 0L, 0L, 0, 0, 0L, 0L, 0);
    }
}
