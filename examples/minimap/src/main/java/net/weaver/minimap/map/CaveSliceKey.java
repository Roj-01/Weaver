package net.weaver.minimap.map;

import java.util.Objects;

/**
 * Identifies one cave slice by region and Y level.
 *
 * @param region the region key, never {@code null}
 * @param y      the sampled Y level
 */
public record CaveSliceKey(RegionKey region, int y)
{
    /**
     * Validates the region key.
     *
     * @throws NullPointerException when {@code region} is {@code null}
     */
    public CaveSliceKey
    {
        Objects.requireNonNull(region, "region");
    }
}
