package net.weaver.api.client;

/**
 * Modifier-mask constants and matching for {@link KeyMappingSpec#modifiers()}.
 *
 * <p>The mask uses the modifier bit values that Minecraft 26.3 exposes through
 * {@code InputConstants.MOD_*}. A mapping runs only when every bit set in the
 * required mask is also set in the current state; extra held modifiers do not
 * block it.
 *
 * <p>The current state is read from the keyboard when a click is consumed.
 * Caps Lock and Num Lock are included for completeness but are not required by
 * the built-in reader. Modifier matching applies to keyboard and mouse
 * mappings alike.
 */
public final class KeyModifiers
{
    /** No modifier is required. */
    public static final int NONE = 0;

    /** Shift, either side. */
    public static final int SHIFT = 0x0003;

    /** Control, either side. */
    public static final int CONTROL = 0x00C0;

    /** Alt, either side. */
    public static final int ALT = 0x0300;

    /** Super/Command, either side. */
    public static final int SUPER = 0x0C00;

    /** Num Lock. */
    public static final int NUM_LOCK = 0x1000;

    /** Caps Lock. */
    public static final int CAPS_LOCK = 0x2000;

    private KeyModifiers()
    {
    }

    /**
     * Reports whether the current modifier state satisfies the required mask.
     *
     * <p>A required mask of {@link #NONE} always matches. A current state with
     * additional bits set still matches as long as every required bit is
     * present.
     *
     * @param required the modifier bits the mapping requires
     * @param current  the modifier bits currently held
     * @return {@code true} when every required bit is held
     */
    public static boolean matches(int required, int current)
    {
        return (current & required) == required;
    }
}
