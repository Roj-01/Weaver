package net.weaver.minimap.map;

import java.util.Objects;

/**
 * Entry points for deterministic cave slice scanning.
 */
public final class CaveSliceScanner
{
    private CaveSliceScanner()
    {
    }

    /**
     * Scans a full slice immediately. Prefer {@link CaveSliceBuild} on the
     * client thread so the work can be spread across ticks.
     *
     * @param source  the block source, never {@code null}
     * @param key     the region key, never {@code null}
     * @param y       the sampled Y level
     * @param palette the dimension palette, never {@code null}
     * @return the completed slice, never {@code null}
     */
    public static CaveSlice scan(CaveBlockSource source, RegionKey key, int y,
                                 CavePalette palette)
    {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(palette, "palette");
        CaveSliceBuild build = new CaveSliceBuild(source, key, y, palette);
        build.step(RegionData.CHUNKS_PER_REGION * RegionData.CHUNKS_PER_REGION);
        return build.slice();
    }
}
