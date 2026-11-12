package net.weaver.minimap.map;

/**
 * Lighting model for the surface map.
 *
 * <p>Three effects are stacked, in this order, and all of them are needed for
 * the map to read as terrain instead of as a colouring book:</p>
 *
 * <ol>
 *   <li><b>Slope light.</b> Every column exposes a normal built from the height
 *       difference to its north and west neighbours. A dot with a fixed
 *       north-west sun gives the classic relief shading: faces turned towards
 *       the light brighten, faces turned away darken. This is what makes
 *       mountains, ravines and coastlines legible at a glance.</li>
 *   <li><b>Ambient colour.</b> The shaded side is not grey. Overworld shadow is
 *       cool blue, the Nether's is red, so the relief keeps the palette of the
 *       dimension it belongs to and never turns muddy.</li>
 *   <li><b>Depth.</b> Low ground sits darker than high ground, which separates
 *       a valley floor from the ridge above it even when both are grass.</li>
 * </ol>
 *
 * <p>Daylight is folded into the same curve: sky light and block light are
 * combined with a soft floor of nine, so a lit surface is at full brightness
 * while a roofed or night-time surface keeps about a third of it. The map dims
 * at night the way the world does, instead of glowing like a lamp.</p>
 */
public final class SurfaceShading
{
    /** How much of the ambient shadow colour reaches the shaded side. */
    public static final double AMBIENT_COLORED = 0.2D;

    /** Flat light applied to every column, lit or not. */
    public static final double AMBIENT_WHITE = 0.5D;

    /** Direct light on a face aimed straight at the sun. */
    public static final double MAX_DIRECT = 0.6666667D;

    /** Compression applied to the quantized cosine. */
    public static final double DIRECT_SCALE = 0.88388D;

    /** Soft floor of the light curve, in light levels. */
    public static final double LIGHT_FLOOR = 9.0D;

    /** Normaliser for the light curve: {@code LIGHT_FLOOR + 15}. */
    public static final double LIGHT_RANGE = 24.0D;

    /** Lowest depth multiplier applied to low ground. */
    public static final double MIN_DEPTH = 0.7D;

    /** Highest depth multiplier applied to high ground. */
    public static final double MAX_DEPTH = 1.15D;

    /** Height span over which the depth multiplier moves from min to max. */
    public static final double DEPTH_SPAN = 96.0D;

    private SurfaceShading()
    {
    }

    /**
     * Returns the light level multiplier for one column.
     *
     * @param sky        the sky light at the column, from 0 to 15
     * @param blockLight the block light at the column, from 0 to 15
     * @param day        the daylight factor, from 0 (night) to 1 (noon)
     * @return a multiplier from about 0.37 to 1.0
     */
    public static double brightness(int sky, int blockLight, double day)
    {
        double sun = Math.max(0.0D, Math.min(1.0D, day)) * 15.0D;
        double light = Math.max(sun, Math.max(0.0D, Math.min(15.0D, blockLight)));
        return Math.max(0.0D, Math.min(1.0D, (LIGHT_FLOOR + light) / LIGHT_RANGE));
    }

    /**
     * Writes the three channel multipliers for a surface column.
     *
     * <p>The normal is {@code (-dx, 1, -dz)} and the sun sits on the north-west
     * diagonal, which is the same direction vanilla shades its block faces
     * from, so the map and the world agree about which side of a hill is lit.</p>
     *
     * @param dx      the height difference to the west neighbour
     * @param dz      the height difference to the north neighbour
     * @param shadowR the ambient shadow red, from 0 to 1
     * @param shadowG the ambient shadow green, from 0 to 1
     * @param shadowB the ambient shadow blue, from 0 to 1
     * @param out     a three-element array that receives R, G and B, in that
     *                order; the caller owns and reuses it
     */
    public static void light(int dx, int dz, double shadowR, double shadowG, double shadowB,
                             double[] out)
    {
        double magnitude = Math.sqrt((double) dx * dx + 1.0D + (double) dz * dz);
        double cosine = (dx + 1.0D + dz) / (magnitude * Math.sqrt(3.0D));
        double direct = cosine <= 0.0D ? 0.0D : Math.min(1.0D, cosine) * MAX_DIRECT * DIRECT_SCALE;
        double white = AMBIENT_WHITE + direct;
        out[0] = shadowR * AMBIENT_COLORED + white;
        out[1] = shadowG * AMBIENT_COLORED + white;
        out[2] = shadowB * AMBIENT_COLORED + white;
    }

    /**
     * Returns the depth multiplier for a column.
     *
     * @param height   the column surface height
     * @param seaLevel the level's sea level, used as the neutral height
     * @return a multiplier between {@link #MIN_DEPTH} and {@link #MAX_DEPTH}
     */
    public static double depth(int height, int seaLevel)
    {
        double factor = (height - seaLevel) / DEPTH_SPAN + 1.0D;
        return Math.max(MIN_DEPTH, Math.min(MAX_DEPTH, factor));
    }

    /**
     * Multiplies one colour by three channel factors.
     *
     * @param argb  the source colour
     * @param red   the red multiplier
     * @param green the green multiplier
     * @param blue  the blue multiplier
     * @return an opaque ARGB colour
     */
    public static int scale(int argb, double red, double green, double blue)
    {
        int r = clamp((int) Math.round(((argb >> 16) & 0xFF) * red));
        int g = clamp((int) Math.round(((argb >> 8) & 0xFF) * green));
        int b = clamp((int) Math.round((argb & 0xFF) * blue));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value)
    {
        return Math.max(0, Math.min(255, value));
    }
}
