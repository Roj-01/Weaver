package net.weaver.core.data;

/**
 * Hard bounds for Weaver capability data persisted on runtime holders such as
 * entities, block entities, levels, and chunks.
 *
 * <p>Values live in one child carrier keyed by {@link #ROOT_KEY} and do not
 * touch any other key in the surrounding game data. Holder data is saved with
 * the world and never sent with an item stack, so a value may be larger than
 * the {@link ItemDataLimits} allow: up to 1 MiB, which only a capability
 * declared {@code CapabilitySync.NONE} can reach (the others stay bounded at
 * 16384 bytes by their type). Every holder type shares the same per-value,
 * entry-count, and total-size budget.</p>
 */
public final class HolderDataLimits
{
    /** Maximum capability key length in characters. */
    public static final int MAX_KEY_CHARS = 128;

    /** Maximum encoded size of one capability value in bytes. */
    public static final int MAX_VALUE_BYTES = 1 << 20;

    /** Maximum number of capability entries in the Weaver root carrier. */
    public static final int MAX_ENTRIES = 64;

    /** Maximum total size of the Weaver root carrier in bytes. */
    public static final int MAX_TOTAL_BYTES = 2 << 20;

    /** Key of the nested carrier that holds all Weaver capability values. */
    public static final String ROOT_KEY = "weaver:capabilities";

    private HolderDataLimits()
    {
    }
}
