package net.weaver.minimap.map;

import java.util.Objects;

/**
 * Identifies one 256x256-block region of a dimension.
 *
 * <p>The dimension is stored verbatim. Region coordinates use floor division:
 * region {@code 0} covers blocks {@code 0} through {@code 255}, and region
 * {@code -1} covers blocks {@code -256} through {@code -1}.</p>
 *
 * @param dimension the dimension identifier, never {@code null} or blank
 * @param regionX   the region X coordinate
 * @param regionZ   the region Z coordinate
 */
public record RegionKey(String dimension, int regionX, int regionZ)
{
    /**
     * Validates the dimension identifier.
     *
     * @throws NullPointerException     when {@code dimension} is {@code null}
     * @throws IllegalArgumentException when {@code dimension} is blank
     */
    public RegionKey
    {
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.isBlank())
        {
            throw new IllegalArgumentException("dimension must not be blank");
        }
    }

    /**
     * Returns the region containing a block position.
     *
     * <p>The arithmetic shift is floor division by 256, so negative block
     * coordinates map to negative region coordinates.</p>
     *
     * @param dimension the dimension identifier, never {@code null} or blank
     * @param blockX    the block X coordinate
     * @param blockZ    the block Z coordinate
     * @return the containing region key, never {@code null}
     * @throws NullPointerException     when {@code dimension} is {@code null}
     * @throws IllegalArgumentException when {@code dimension} is blank
     */
    public static RegionKey ofBlock(String dimension, int blockX, int blockZ)
    {
        return new RegionKey(dimension, blockX >> 8, blockZ >> 8);
    }

    /**
     * Returns the block X coordinate of this region's minimum corner.
     *
     * @return the minimum block X coordinate
     */
    public int minBlockX()
    {
        return regionX << 8;
    }

    /**
     * Returns the block Z coordinate of this region's minimum corner.
     *
     * @return the minimum block Z coordinate
     */
    public int minBlockZ()
    {
        return regionZ << 8;
    }

    /**
     * Returns the storage file name for this region.
     *
     * @return the file name in the form {@code r.<x>.<z>.wmap}
     */
    public String fileName()
    {
        return "r." + regionX + "." + regionZ + ".wmap";
    }
}
