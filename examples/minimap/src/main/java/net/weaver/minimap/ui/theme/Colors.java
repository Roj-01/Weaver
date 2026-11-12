package net.weaver.minimap.ui.theme;

/**
 * Small, allocation-free ARGB colour helpers shared by the visual system.
 */
public final class Colors
{
    public static final int WHITE = 0xFFFFFFFF;
    public static final int BLACK = 0xFF000000;
    public static final int TRANSPARENT = 0x00000000;

    private static final float[] LINEAR = new float[256];

    static
    {
        for (int index = 0; index < LINEAR.length; index++)
        {
            float channel = index / 255.0F;
            LINEAR[index] = channel <= 0.04045F
                    ? channel / 12.92F
                    : (float) Math.pow((channel + 0.055F) / 1.055F, 2.4F);
        }
    }

    private Colors()
    {
    }

    /** The alpha channel of a packed ARGB colour. */
    public static int alpha(int argb)
    {
        return (argb >>> 24) & 0xFF;
    }

    /** The red channel of a packed ARGB colour. */
    public static int red(int argb)
    {
        return (argb >>> 16) & 0xFF;
    }

    /** The green channel of a packed ARGB colour. */
    public static int green(int argb)
    {
        return (argb >>> 8) & 0xFF;
    }

    /** The blue channel of a packed ARGB colour. */
    public static int blue(int argb)
    {
        return argb & 0xFF;
    }

    /** The RGB part of a packed ARGB colour. */
    public static int rgb(int argb)
    {
        return argb & 0x00FFFFFF;
    }

    /** Packs channels into ARGB, clamping each to 0..255. */
    public static int argb(int alpha, int red, int green, int blue)
    {
        return (clamp(alpha, 0, 255) << 24)
                | (clamp(red, 0, 255) << 16)
                | (clamp(green, 0, 255) << 8)
                | clamp(blue, 0, 255);
    }

    /** Replaces the alpha channel. */
    public static int withAlpha(int argb, int alpha)
    {
        return (clamp(alpha, 0, 255) << 24) | rgb(argb);
    }

    /** Scales the existing alpha channel by a 0..1 factor. */
    public static int scaleAlpha(int argb, double factor)
    {
        return withAlpha(argb, (int) Math.round(alpha(argb) * clamp01(factor)));
    }

    /** Blends two colours in sRGB space; {@code t} of 0 returns {@code from}. */
    public static int mix(int from, int to, double t)
    {
        double clamped = clamp01(t);
        int alpha = (int) Math.round(alpha(from) + (alpha(to) - alpha(from)) * clamped);
        int red = (int) Math.round(red(from) + (red(to) - red(from)) * clamped);
        int green = (int) Math.round(green(from) + (green(to) - green(from)) * clamped);
        int blue = (int) Math.round(blue(from) + (blue(to) - blue(from)) * clamped);
        return argb(alpha, red, green, blue);
    }

    /** Moves a colour towards white. */
    public static int lighten(int argb, double amount)
    {
        return mix(argb, WHITE, amount);
    }

    /** Moves a colour towards black. */
    public static int darken(int argb, double amount)
    {
        return mix(argb, BLACK, amount);
    }

    /** The WCAG relative luminance of an opaque colour. */
    public static double luminance(int argb)
    {
        double red = LINEAR[red(argb)];
        double green = LINEAR[green(argb)];
        double blue = LINEAR[blue(argb)];
        return 0.2126D * red + 0.7152D * green + 0.0722D * blue;
    }

    /** The WCAG contrast ratio between two opaque colours. */
    public static double contrastRatio(int first, int second)
    {
        double a = luminance(first);
        double b = luminance(second);
        double lighter = Math.max(a, b);
        double darker = Math.min(a, b);
        return (lighter + 0.05D) / (darker + 0.05D);
    }

    /** {@code true} when the colour is perceptually dark. */
    public static boolean isDark(int argb)
    {
        return luminance(argb) < 0.42D;
    }

    /**
     * Picks the readable text colour for a background.
     *
     * @param background the background ARGB colour
     * @return black or white, whichever has the higher contrast
     */
    public static int contrastText(int background)
    {
        return contrastRatio(background, WHITE) >= contrastRatio(background, BLACK)
                ? WHITE : BLACK;
    }

    /** Clamps a value into an inclusive range. */
    public static int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }

    /** Clamps a value into an inclusive range. */
    public static double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }

    /** Clamps a value into 0..1. */
    public static double clamp01(double value)
    {
        return value < 0.0D ? 0.0D : Math.min(value, 1.0D);
    }
}
