package net.weaver.core.data;

/**
 * Hard bounds for Weaver capability data persisted inside a game item stack's
 * {@code minecraft:custom_data} data component.
 *
 * <p>All capability values live in one nested compound keyed by
 * {@link #ROOT_KEY}. Nothing outside that compound is read, written, or
 * removed by {@link ItemDataAccess}. The limits exist so a single item can
 * never carry an unbounded payload.</p>
 */
public final class ItemDataLimits
{
    /** Maximum capability key length in characters. */
    public static final int MAX_KEY_CHARS = 128;

    /** Maximum encoded size of one capability value in bytes. */
    public static final int MAX_VALUE_BYTES = 16384;

    /** Maximum number of capability entries in the Weaver root compound. */
    public static final int MAX_ENTRIES = 64;

    /** Maximum NBT size of the Weaver root compound in bytes. */
    public static final int MAX_TOTAL_BYTES = 32768;

    /** Key of the nested compound that holds all Weaver capability values. */
    public static final String ROOT_KEY = "weaver:capabilities";

    private ItemDataLimits()
    {
    }
}
