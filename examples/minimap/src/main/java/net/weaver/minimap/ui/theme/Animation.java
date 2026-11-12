package net.weaver.minimap.ui.theme;

/**
 * A time-based scalar animation.
 *
 * <p>Instances are mutable but allocation free after construction; the value is
 * derived from the wall clock on every query. The default curve is
 * {@link Easing#EASE_OUT_CUBIC}, matching the 120 ms hover and 180 ms tab
 * timings from the visual specification.</p>
 */
public final class Animation
{
    private Easing easing;
    private long startMillis;
    private long durationMillis;
    private double from;
    private double to;

    /**
     * Creates an animation with the default easing.
     *
     * @param startMillis    the start time
     * @param durationMillis the duration in milliseconds
     * @param from           the start value
     * @param to             the end value
     */
    public Animation(long startMillis, long durationMillis, double from, double to)
    {
        this(Easing.EASE_OUT_CUBIC, startMillis, durationMillis, from, to);
    }

    /**
     * Creates an animation.
     *
     * @param easing         the curve, never {@code null}
     * @param startMillis    the start time
     * @param durationMillis the duration in milliseconds
     * @param from           the start value
     * @param to             the end value
     */
    public Animation(Easing easing, long startMillis, long durationMillis, double from, double to)
    {
        this.easing = easing == null ? Easing.EASE_OUT_CUBIC : easing;
        this.startMillis = startMillis;
        this.durationMillis = Math.max(0L, durationMillis);
        this.from = from;
        this.to = to;
    }

    /**
     * Creates an animation that starts now.
     *
     * @param from           the start value
     * @param to             the end value
     * @param durationMillis the duration in milliseconds
     * @return the animation
     */
    public static Animation of(double from, double to, long durationMillis)
    {
        return new Animation(System.currentTimeMillis(), durationMillis, from, to);
    }

    /** The raw progress in 0..1, clamped at both ends. */
    public double progress(long now)
    {
        if (durationMillis <= 0L)
        {
            return 1.0D;
        }
        return Colors.clamp01((now - startMillis) / (double) durationMillis);
    }

    /** The eased value at a point in time. */
    public double value(long now)
    {
        return from + (to - from) * easing.apply(progress(now));
    }

    /** The eased value at the current wall-clock time. */
    public double value()
    {
        return value(System.currentTimeMillis());
    }

    /** Whether the animation has reached its end. */
    public boolean isDone(long now)
    {
        return now >= startMillis + durationMillis;
    }

    /** Whether the animation has reached its end at the current time. */
    public boolean isDone()
    {
        return isDone(System.currentTimeMillis());
    }

    /** Restarts the animation from the same start value. */
    public void reset(long now)
    {
        startMillis = now;
    }

    /** Restarts the animation at the current time. */
    public void reset()
    {
        reset(System.currentTimeMillis());
    }

    /** Restarts the animation from the current value towards a new target. */
    public void retarget(double target, long now)
    {
        from = value(now);
        to = target;
        startMillis = now;
    }

    /** The curve. */
    public Easing easing()
    {
        return easing;
    }

    /** Replaces the curve. */
    public void setEasing(Easing easing)
    {
        this.easing = easing == null ? Easing.EASE_OUT_CUBIC : easing;
    }

    /** The start time in milliseconds. */
    public long startMillis()
    {
        return startMillis;
    }

    /** The duration in milliseconds. */
    public long durationMillis()
    {
        return durationMillis;
    }

    /** The start value. */
    public double from()
    {
        return from;
    }

    /** The end value. */
    public double to()
    {
        return to;
    }

    /** A 120 ms hover transition. */
    public static Motion hoverMotion()
    {
        return new Motion(120L);
    }

    /** A 60 ms press transition. */
    public static Motion pressMotion()
    {
        return new Motion(60L);
    }

    /** A 180 ms tab transition. */
    public static Motion tabMotion()
    {
        return new Motion(180L);
    }

    /** A 220 ms panel or dropdown transition. */
    public static Motion panelMotion()
    {
        return new Motion(220L);
    }

    /**
     * A boolean-to-double transition used for hover, press and toggle states.
     *
     * <p>Changing the target restarts the interpolation from the current
     * direction. The value is always in 0..1.</p>
     */
    public static final class Motion
    {
        private final long durationMillis;
        private final Easing easing;
        private boolean target;
        private long changedAt;
        private double from;

        /** Creates a motion with the default 120 ms hover timing. */
        public Motion()
        {
            this(120L);
        }

        /** Creates a motion with an explicit duration. */
        public Motion(long durationMillis)
        {
            this(durationMillis, Easing.EASE_OUT_CUBIC);
        }

        /** Creates a motion with an explicit duration and curve. */
        public Motion(long durationMillis, Easing easing)
        {
            this.durationMillis = Math.max(1L, durationMillis);
            this.easing = easing == null ? Easing.EASE_OUT_CUBIC : easing;
        }

        /** Sets the target, restarting the transition only when it changes. */
        public void set(boolean target, long now)
        {
            if (this.target != target)
            {
                this.from = value(now);
                this.target = target;
                this.changedAt = now;
            }
        }

        /** The target state. */
        public boolean target()
        {
            return target;
        }

        /** The interpolated value in 0..1. */
        public double value(long now)
        {
            double end = target ? 1.0D : 0.0D;
            double progress = (now - changedAt) / (double) durationMillis;
            if (progress >= 1.0D)
            {
                return end;
            }
            if (progress <= 0.0D)
            {
                return from;
            }
            return from + (end - from) * easing.apply(progress);
        }
    }
}
