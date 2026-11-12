package net.weaver.minimap.map;

/**
 * Converts between block coordinates and a square map viewport.
 *
 * <p>The viewport is centered on a world position. {@code zoom} is measured in
 * blocks per pixel, so zoom {@code 1.0} maps one block to one pixel and zoom
 * {@code 2.0} shows twice the area in the same number of pixels. The screen Y
 * axis grows southward, matching Minecraft's positive Z direction.</p>
 *
 * <p>Instances are immutable and safe to share between threads.</p>
 */
public record MapProjection(double centerX, double centerZ, int originX, int originY,
                            int size, double zoom)
{
    /**
     * Validates the viewport geometry.
     *
     * @throws IllegalArgumentException when the size or zoom is not positive,
     *                                  or a coordinate is not finite
     */
    public MapProjection
    {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ))
        {
            throw new IllegalArgumentException("center coordinates must be finite");
        }
        if (!Double.isFinite(zoom) || zoom <= 0.0)
        {
            throw new IllegalArgumentException("zoom must be finite and positive: " + zoom);
        }
        if (size <= 0)
        {
            throw new IllegalArgumentException("size must be positive: " + size);
        }
    }

    /** Pixels per block at the current zoom. */
    public double pixelsPerBlock()
    {
        return 1.0 / zoom;
    }

    /** Half of the visible width in blocks. */
    public double halfWorldSize()
    {
        return size * zoom * 0.5;
    }

    /** The western edge of the visible area. */
    public double minWorldX()
    {
        return centerX - halfWorldSize();
    }

    /** The northern edge of the visible area. */
    public double minWorldZ()
    {
        return centerZ - halfWorldSize();
    }

    /** The eastern edge of the visible area. */
    public double maxWorldX()
    {
        return centerX + halfWorldSize();
    }

    /** The southern edge of the visible area. */
    public double maxWorldZ()
    {
        return centerZ + halfWorldSize();
    }

    /** Converts a world X coordinate to a screen X coordinate. */
    public double screenX(double worldX)
    {
        return originX + size * 0.5D + (worldX - centerX) * pixelsPerBlock();
    }

    /** Converts a world Z coordinate to a screen Y coordinate. */
    public double screenY(double worldZ)
    {
        return originY + size * 0.5D + (worldZ - centerZ) * pixelsPerBlock();
    }

    /** Converts a screen X coordinate back to a world X coordinate. */
    public double worldX(double screenX)
    {
        return centerX + (screenX - originX - size * 0.5D) / pixelsPerBlock();
    }

    /** Converts a screen Y coordinate back to a world Z coordinate. */
    public double worldZ(double screenY)
    {
        return centerZ + (screenY - originY - size * 0.5D) / pixelsPerBlock();
    }

    /** Returns the screen size of a 256-block region. */
    public int regionScreenSize()
    {
        return Math.max(1, (int) Math.round(RegionData.BLOCKS_PER_REGION * pixelsPerBlock()));
    }

    /**
     * Returns a projection with the same viewport but a different center.
     *
     * @param newCenterX the new center X
     * @param newCenterZ the new center Z
     * @return a new projection
     */
    public MapProjection withCenter(double newCenterX, double newCenterZ)
    {
        return new MapProjection(newCenterX, newCenterZ, originX, originY, size, zoom);
    }

    /**
     * Returns a projection with the same center but a different zoom.
     *
     * @param newZoom the new blocks-per-pixel zoom
     * @return a new projection
     */
    public MapProjection withZoom(double newZoom)
    {
        return new MapProjection(centerX, centerZ, originX, originY, size, newZoom);
    }
}
