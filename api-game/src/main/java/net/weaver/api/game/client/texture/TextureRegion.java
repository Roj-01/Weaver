package net.weaver.api.game.client.texture;

/**
 * Validation and row extraction for partial texture uploads.
 *
 * <p>This type is package-private because it is an implementation detail of
 * {@link DynamicTextureHandle#uploadRegion(int, int, int, int, int[])}. It is
 * deliberately game-free so the region math can be tested without a client.
 */
final class TextureRegion
{
    private TextureRegion()
    {
    }

    /**
     * Validates a partial-upload request.
     *
     * @param imageWidth  the full texture width
     * @param imageHeight the full texture height
     * @param x           the region x
     * @param y           the region y
     * @param width       the region width
     * @param height      the region height
     * @param argb        the source pixels
     * @throws IllegalArgumentException when a coordinate or size is invalid or
     *                                  the source array is too small
     */
    static void validate(int imageWidth, int imageHeight, int x, int y, int width,
                         int height, int[] argb)
    {
        if (width < 1 || height < 1)
        {
            throw new IllegalArgumentException("Region size must be at least 1x1 but was "
                    + width + "x" + height);
        }
        if (x < 0 || y < 0)
        {
            throw new IllegalArgumentException("Region origin must be >= 0 but was "
                    + x + "," + y);
        }
        if ((long) x + width > imageWidth || (long) y + height > imageHeight)
        {
            throw new IllegalArgumentException("Region " + x + "," + y + " " + width
                    + "x" + height + " exceeds " + imageWidth + "x" + imageHeight);
        }
        long needed = (long) width * height;
        if (needed > Integer.MAX_VALUE)
        {
            throw new IllegalArgumentException("Region is too large: " + width + "x" + height);
        }
        if (argb.length < needed)
        {
            throw new IllegalArgumentException("Expected at least " + needed
                    + " pixels but got " + argb.length);
        }
    }

    /**
     * Copies the first {@code width * height} pixels of a row-major region.
     *
     * <p>The source array may be larger; only the region entries are read.
     *
     * @param argb   the region pixels
     * @param width  the region width
     * @param height the region height
     * @return a new row-major array with {@code width * height} entries
     */
    static int[] copyRegion(int[] argb, int width, int height)
    {
        int[] region = new int[width * height];
        System.arraycopy(argb, 0, region, 0, region.length);
        return region;
    }
}
