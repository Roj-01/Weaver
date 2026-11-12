package net.weaver.minimap.map;

/**
 * Immutable snapshot of cave mode for UI status and loading indicators.
 *
 * @param enabled       whether cave mode is enabled in the settings
 * @param auto          whether the Y is chosen automatically
 * @param caveY         the selected Y, or {@link Integer#MIN_VALUE} when none
 * @param ready         whether the player's current slice is available
 * @param depth         blocks between the player and the selected slice
 * @param pendingSlices slices queued for disk load or scan
 * @param loadedSlices  slices currently in the memory cache
 * @param scannedSlices slices produced by the scanner since world join
 */
public record CaveState(boolean enabled, boolean auto, int caveY, boolean ready, int depth,
                        int pendingSlices, int loadedSlices, long scannedSlices)
{
    /**
     * Returns a disabled snapshot.
     *
     * @return an empty snapshot, never {@code null}
     */
    public static CaveState disabled()
    {
        return new CaveState(false, false, Integer.MIN_VALUE, false, 0, 0, 0, 0L);
    }
}
