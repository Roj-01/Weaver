package net.weaver.minimap.map;

import java.util.Objects;

/**
 * One horizontal cave layer sampled at a fixed Y inside a 256x256 region.
 *
 * <p>Colors use the same row-major layout as {@link RegionData}:
 * {@code index = localZ * BLOCKS_PER_REGION + localX}. A pixel is present when
 * the scanner saw a non-air block there; air is the transparent negative space
 * of the cave map.</p>
 *
 * <p>One slice holds a 256 KiB ARGB array and a 64 KiB boolean mask, about
 * 320 KiB of heap. The cave cache is bounded by
 * {@link MapSettings#caveCacheSize()}.</p>
 */
public final class CaveSlice
{
    private final RegionKey key;
    private final int y;
    private final int[] colors;
    private final boolean[] present;

    /**
     * Creates an all-air slice.
     *
     * @param key the region key, never {@code null}
     * @param y   the sampled Y level
     * @throws NullPointerException when {@code key} is {@code null}
     */
    public CaveSlice(RegionKey key, int y)
    {
        this.key = Objects.requireNonNull(key, "key");
        this.y = y;
        this.colors = new int[RegionData.BLOCKS_PER_REGION * RegionData.BLOCKS_PER_REGION];
        this.present = new boolean[RegionData.BLOCKS_PER_REGION * RegionData.BLOCKS_PER_REGION];
    }

    /** Returns the region key. */
    public RegionKey key()
    {
        return key;
    }

    /** Returns the sampled Y level. */
    public int y()
    {
        return y;
    }

    /** Returns the internal ARGB pixel array. */
    public int[] colors()
    {
        return colors;
    }

    /** Returns the internal presence mask. */
    public boolean[] present()
    {
        return present;
    }

    /**
     * Reads one pixel color.
     *
     * @throws IndexOutOfBoundsException when a coordinate is outside the region
     */
    public int colorAt(int localX, int localZ)
    {
        checkBlock(localX, localZ);
        return colors[localZ * RegionData.BLOCKS_PER_REGION + localX];
    }

    /**
     * Writes one pixel color.
     *
     * @throws IndexOutOfBoundsException when a coordinate is outside the region
     */
    public void setColor(int localX, int localZ, int argb)
    {
        checkBlock(localX, localZ);
        colors[localZ * RegionData.BLOCKS_PER_REGION + localX] = argb;
    }

    /**
     * Tests whether one pixel has visible data.
     *
     * @throws IndexOutOfBoundsException when a coordinate is outside the region
     */
    public boolean isPresent(int localX, int localZ)
    {
        checkBlock(localX, localZ);
        return present[localZ * RegionData.BLOCKS_PER_REGION + localX];
    }

    /**
     * Sets whether one pixel has visible data.
     *
     * @throws IndexOutOfBoundsException when a coordinate is outside the region
     */
    public void setPresent(int localX, int localZ, boolean visible)
    {
        checkBlock(localX, localZ);
        present[localZ * RegionData.BLOCKS_PER_REGION + localX] = visible;
    }

    /** Returns whether no pixel has visible data. */
    public boolean isEmpty()
    {
        for (boolean visible : present)
        {
            if (visible)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a deep copy.
     *
     * @return an independent slice, never {@code null}
     */
    public CaveSlice copy()
    {
        CaveSlice copy = new CaveSlice(key, y);
        System.arraycopy(colors, 0, copy.colors, 0, colors.length);
        System.arraycopy(present, 0, copy.present, 0, present.length);
        return copy;
    }

    private static void checkBlock(int localX, int localZ)
    {
        if (localX < 0 || localX >= RegionData.BLOCKS_PER_REGION)
        {
            throw new IndexOutOfBoundsException(
                    "localX must be between 0 and 255 inclusive: " + localX);
        }
        if (localZ < 0 || localZ >= RegionData.BLOCKS_PER_REGION)
        {
            throw new IndexOutOfBoundsException(
                    "localZ must be between 0 and 255 inclusive: " + localZ);
        }
    }
}
