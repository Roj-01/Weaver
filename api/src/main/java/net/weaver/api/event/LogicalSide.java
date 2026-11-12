package net.weaver.api.event;

/**
 * The logical side a callback or event belongs to.
 *
 * <p>This is deliberately separate from {@link Side}. {@code Side} describes the
 * physical process a mod was loaded into and is used as a load-time filter.
 * {@code LogicalSide} describes which side logically produced an event or is
 * being invoked, which is not the same thing on a client that runs an
 * integrated server: both logical sides exist in one process there.
 *
 * <p>{@code BOTH} matches either side, mirroring {@link Side#BOTH}.
 */
public enum LogicalSide {
    CLIENT,
    SERVER,
    BOTH;

    /** True for {@code CLIENT} and {@code BOTH}. */
    public boolean isClient() {
        return this == CLIENT || this == BOTH;
    }

    /** True for {@code SERVER} and {@code BOTH}. */
    public boolean isServer() {
        return this == SERVER || this == BOTH;
    }

    /** True when either value is {@code BOTH} or the two values are equal. */
    public boolean matches(LogicalSide other) {
        return this == BOTH || other == BOTH || this == other;
    }

    /**
     * Maps a physical {@link Side} to its logical counterpart. {@code BOTH}
     * maps to {@code BOTH}; {@code null} is treated as {@code BOTH}.
     */
    public static LogicalSide from(Side side) {
        if (side == null) {
            return BOTH;
        }
        return switch (side) {
            case CLIENT -> CLIENT;
            case SERVER -> SERVER;
            case BOTH -> BOTH;
        };
    }
}
