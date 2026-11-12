package net.weaver.minimap.render;

/**
 * Converts between block coordinates and an arbitrarily rotated map viewport.
 *
 * <p>The viewport is centred on a world position. {@code zoom} is measured in
 * blocks per pixel. Rotation is clockwise in degrees and rotates the map content
 * on screen; a rotation of {@code 0} is north-up. Screen Y grows southward,
 * matching Minecraft's positive Z direction.</p>
 *
 * <p>Instances are immutable and allocation free.</p>
 *
 * @param centerX        the view centre X
 * @param centerZ        the view centre Z
 * @param originX        the viewport left edge
 * @param originY        the viewport top edge
 * @param width          the viewport width in pixels
 * @param height         the viewport height in pixels
 * @param zoom           blocks per pixel
 * @param rotationDegrees the clockwise content rotation in degrees
 */
public record MapView(double centerX, double centerZ, int originX, int originY, int width,
                      int height, double zoom, double rotationDegrees)
{
    /**
     * Validates the geometry.
     *
     * @throws IllegalArgumentException when the size or zoom is not positive,
     *                                  or a coordinate is not finite
     */
    public MapView
    {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ))
        {
            throw new IllegalArgumentException("center coordinates must be finite");
        }
        if (!Double.isFinite(zoom) || zoom <= 0.0D)
        {
            throw new IllegalArgumentException("zoom must be finite and positive: " + zoom);
        }
        if (width <= 0 || height <= 0)
        {
            throw new IllegalArgumentException("viewport must be positive: " + width
                    + "x" + height);
        }
        if (!Double.isFinite(rotationDegrees))
        {
            throw new IllegalArgumentException("rotation must be finite");
        }
    }

    /** Pixels per block at the current zoom. */
    public double pixelsPerBlock()
    {
        return 1.0D / zoom;
    }

    /** The sine of the content rotation. */
    public double sin()
    {
        return Math.sin(Math.toRadians(rotationDegrees));
    }

    /** The cosine of the content rotation. */
    public double cos()
    {
        return Math.cos(Math.toRadians(rotationDegrees));
    }

    /** Half of the visible width in blocks. */
    public double halfWorldWidth()
    {
        return width * zoom * 0.5D;
    }

    /** Half of the visible height in blocks. */
    public double halfWorldHeight()
    {
        return height * zoom * 0.5D;
    }

    /** The western edge before rotation. */
    public double minWorldX()
    {
        return centerX - halfWorldWidth();
    }

    /** The northern edge before rotation. */
    public double minWorldZ()
    {
        return centerZ - halfWorldHeight();
    }

    /** The eastern edge before rotation. */
    public double maxWorldX()
    {
        return centerX + halfWorldWidth();
    }

    /** The southern edge before rotation. */
    public double maxWorldZ()
    {
        return centerZ + halfWorldHeight();
    }

    /** Converts a world position to a screen X coordinate. */
    public double screenX(double worldX, double worldZ)
    {
        double dx = (worldX - centerX) / zoom;
        double dz = (worldZ - centerZ) / zoom;
        double cos = cos();
        double sin = sin();
        return originX + width * 0.5D + dx * cos - dz * sin;
    }

    /** Converts a world position to a screen Y coordinate. */
    public double screenY(double worldX, double worldZ)
    {
        double dx = (worldX - centerX) / zoom;
        double dz = (worldZ - centerZ) / zoom;
        double cos = cos();
        double sin = sin();
        return originY + height * 0.5D + dx * sin + dz * cos;
    }

    /** Converts a screen position back to a world X coordinate. */
    public double worldX(double screenX, double screenY)
    {
        double dx = screenX - originX - width * 0.5D;
        double dy = screenY - originY - height * 0.5D;
        double cos = cos();
        double sin = sin();
        return centerX + (dx * cos + dy * sin) * zoom;
    }

    /** Converts a screen position back to a world Z coordinate. */
    public double worldZ(double screenX, double screenY)
    {
        double dx = screenX - originX - width * 0.5D;
        double dy = screenY - originY - height * 0.5D;
        double cos = cos();
        double sin = sin();
        return centerZ + (-dx * sin + dy * cos) * zoom;
    }

    /** Whether a screen point lies inside the viewport with a margin. */
    public boolean contains(double screenX, double screenY, double margin)
    {
        return screenX >= originX + margin && screenX <= originX + width - margin
                && screenY >= originY + margin && screenY <= originY + height - margin;
    }

    /** Whether a world position is inside the viewport with a margin. */
    public boolean containsWorld(double worldX, double worldZ, double marginPixels)
    {
        return contains(screenX(worldX, worldZ), screenY(worldX, worldZ), marginPixels);
    }

    /** Returns the screen size of a 256-block region. */
    public int regionScreenSize()
    {
        return Math.max(1, (int) Math.round(256.0D * pixelsPerBlock()));
    }

    /** Returns the screen distance for a number of blocks. */
    public double screenLength(double blocks)
    {
        return blocks * pixelsPerBlock();
    }

    /**
     * Returns a view with a different centre.
     *
     * @param newCenterX the new centre X
     * @param newCenterZ the new centre Z
     * @return a new view
     */
    public MapView withCenter(double newCenterX, double newCenterZ)
    {
        return new MapView(newCenterX, newCenterZ, originX, originY, width, height, zoom,
                rotationDegrees);
    }

    /**
     * Returns a view with a different zoom.
     *
     * @param newZoom the new blocks-per-pixel value
     * @return a new view
     */
    public MapView withZoom(double newZoom)
    {
        return new MapView(centerX, centerZ, originX, originY, width, height, newZoom,
                rotationDegrees);
    }

    /**
     * Returns a view with a different rotation.
     *
     * @param newRotation the new clockwise rotation in degrees
     * @return a new view
     */
    public MapView withRotation(double newRotation)
    {
        return new MapView(centerX, centerZ, originX, originY, width, height, zoom,
                newRotation);
    }
}
