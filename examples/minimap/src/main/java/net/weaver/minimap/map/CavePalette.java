package net.weaver.minimap.map;

/**
 * Dimension-specific cave palettes.
 *
 * <p>Solid blocks keep some of their map color but are blended toward the
 * palette tint so a cave reads as a distinct layer. The cave palette is
 * blue-grey, the Nether palette is warm, and the End palette is desaturated
 * violet.</p>
 */
public enum CavePalette
{
    CAVE(0xFF55616B, 0xFF0D141A),
    NETHER(0xFF8A4630, 0xFF1A0A06),
    END(0xFF5D507C, 0xFF120D1E);

    private static final double TINT_WEIGHT = 0.42D;
    private static final double MIN_SHADE = 0.55D;
    private static final double MAX_SHADE = 1.40D;

    private final int tint;
    private final int voidColor;

    CavePalette(int tint, int voidColor)
    {
        this.tint = tint;
        this.voidColor = voidColor;
    }

    /**
     * Selects a palette from a dimension identifier.
     *
     * @param dimensionId the dimension id, possibly {@code null}
     * @return the matching palette, never {@code null}
     */
    public static CavePalette forDimension(String dimensionId)
    {
        if (dimensionId == null)
        {
            return CAVE;
        }
        if (dimensionId.equals("minecraft:the_nether") || dimensionId.endsWith(":the_nether"))
        {
            return NETHER;
        }
        if (dimensionId.equals("minecraft:the_end") || dimensionId.endsWith(":the_end"))
        {
            return END;
        }
        return CAVE;
    }

    /** The tint color used for solid blocks. */
    public int tint()
    {
        return tint;
    }

    /** The dark color used for void columns. */
    public int voidColor()
    {
        return voidColor;
    }

    /**
     * Blends a block map color toward this palette and applies a luminance
     * shade so stone, deepslate, and ore colors remain distinguishable.
     *
     * @param argb the base block color
     * @return an opaque palette color
     */
    public int tintSolid(int argb)
    {
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        int tintRed = (tint >> 16) & 0xFF;
        int tintGreen = (tint >> 8) & 0xFF;
        int tintBlue = tint & 0xFF;
        int blendedRed = blendChannel(red, tintRed, TINT_WEIGHT);
        int blendedGreen = blendChannel(green, tintGreen, TINT_WEIGHT);
        int blendedBlue = blendChannel(blue, tintBlue, TINT_WEIGHT);
        int luminance = (red * 30 + green * 59 + blue * 11) / 100;
        double factor = 0.70D + luminance / 255.0D * 0.30D;
        return 0xFF000000 | (shadeChannel(blendedRed, factor) << 16)
                | (shadeChannel(blendedGreen, factor) << 8)
                | shadeChannel(blendedBlue, factor);
    }

    /**
     * Multiplies the RGB channels of a color, keeping it opaque.
     *
     * @param argb   the ARGB color
     * @param factor the multiplier
     * @return an opaque shaded color
     */
    public static int shade(int argb, double factor)
    {
        double clamped = Math.max(MIN_SHADE, Math.min(MAX_SHADE, factor));
        return 0xFF000000 | (shadeChannel((argb >> 16) & 0xFF, clamped) << 16)
                | (shadeChannel((argb >> 8) & 0xFF, clamped) << 8)
                | shadeChannel(argb & 0xFF, clamped);
    }

    private static int blendChannel(int base, int tint, double amount)
    {
        return clamp((int) Math.round(base * (1.0D - amount) + tint * amount));
    }

    private static int shadeChannel(int channel, double factor)
    {
        return clamp((int) Math.round(channel * factor));
    }

    private static int clamp(int value)
    {
        return Math.max(0, Math.min(255, value));
    }
}
