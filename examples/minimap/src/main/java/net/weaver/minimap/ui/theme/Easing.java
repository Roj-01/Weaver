package net.weaver.minimap.ui.theme;

/**
 * Easing curves used by the visual system.
 *
 * <p>All curves accept an unclamped progress and clamp it to 0..1 first, so
 * callers never see values outside the authored range. Every curve returns
 * exactly {@code 0} at {@code 0} and exactly {@code 1} at {@code 1}.</p>
 */
public enum Easing
{
    LINEAR,
    EASE_OUT_CUBIC,
    EASE_IN_OUT_CUBIC,
    SPRING_OUT;

    /**
     * Applies the curve.
     *
     * @param progress raw progress
     * @return the eased value in 0..1
     */
    public double apply(double progress)
    {
        double t = Colors.clamp01(progress);
        if (t <= 0.0D)
        {
            return 0.0D;
        }
        if (t >= 1.0D)
        {
            return 1.0D;
        }
        return switch (this)
        {
            case LINEAR -> t;
            case EASE_OUT_CUBIC -> 1.0D - Math.pow(1.0D - t, 3.0D);
            case EASE_IN_OUT_CUBIC -> t < 0.5D
                    ? 4.0D * t * t * t
                    : 1.0D - Math.pow(-2.0D * t + 2.0D, 3.0D) / 2.0D;
            case SPRING_OUT -> 1.0D - Math.exp(-7.0D * t) * (1.0D + 7.0D * t);
        };
    }
}
