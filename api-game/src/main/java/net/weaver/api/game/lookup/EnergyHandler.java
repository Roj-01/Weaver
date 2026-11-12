package net.weaver.api.game.lookup;

/**
 * Stored energy that other code can add to and take from. Units are up to
 * the mods that agree on them; amounts are never negative.
 */
public interface EnergyHandler {
    /** The energy stored now. */
    long stored();

    /** The most energy this can store. */
    long capacity();

    /**
     * Adds up to {@code amount}.
     *
     * @return the amount accepted (or that would be)
     */
    long insert(long amount, boolean simulate);

    /**
     * Takes up to {@code amount}.
     *
     * @return the amount taken (or that would be)
     */
    long extract(long amount, boolean simulate);

    /** Whether {@link #insert} can ever accept energy. */
    default boolean canInsert() {
        return true;
    }

    /** Whether {@link #extract} can ever give energy. */
    default boolean canExtract() {
        return true;
    }
}
