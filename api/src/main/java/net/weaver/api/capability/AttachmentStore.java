package net.weaver.api.capability;

/**
 * Storage backend for capability values.
 *
 * <p>A store decides which holders and capabilities it accepts through
 * {@link #supports(Object, Capability)}. Holders are identity-keyed: two equal
 * but distinct holder instances are different holders. {@link #get(Object,
 * Capability)} returns {@code null} when no explicit value is present and must
 * not substitute a capability default; the registry applies defaults.
 *
 * <p>Core installs an ItemStack store that handles item persistence. The
 * built-in {@link RuntimeAttachmentStore} is the fallback for every other
 * holder and is always consulted last.
 */
public interface AttachmentStore {

    /**
     * Returns the consultation priority.
     *
     * <p>Higher priority stores are consulted first. Equal priorities keep
     * registration order.
     *
     * @return the priority, defaulting to {@code 0}
     */
    default int priority() {
        return 0;
    }

    /**
     * Tests whether this store handles a holder and capability pair.
     *
     * @param holder     the holder to test
     * @param capability the capability to test
     * @return {@code true} when this store may hold values for the pair
     */
    boolean supports(Object holder, Capability<?> capability);

    /**
     * Returns the explicit value for a holder and capability.
     *
     * @param holder     the holder
     * @param capability the capability
     * @param <T>        the value type
     * @return the explicit value, or {@code null} when absent
     * @throws CapabilityException when a stored payload is corrupt
     */
    <T> T get(Object holder, Capability<T> capability);

    /**
     * Stores an explicit value and returns the previous explicit value.
     *
     * @param holder     the holder
     * @param capability the capability
     * @param value      the non-null value to store
     * @param <T>        the value type
     * @return the previous explicit value, or {@code null} when none was present
     * @throws CapabilityException when the value cannot be stored
     */
    <T> T set(Object holder, Capability<T> capability, T value);

    /**
     * Removes the explicit value and returns it.
     *
     * @param holder     the holder
     * @param capability the capability
     * @param <T>        the value type
     * @return the removed explicit value, or {@code null} when none was present
     * @throws CapabilityException when the value cannot be removed
     */
    <T> T remove(Object holder, Capability<T> capability);

    /**
     * Tests whether an explicit value is present.
     *
     * @param holder     the holder
     * @param capability the capability
     * @return {@code true} when an explicit value is present
     */
    boolean has(Object holder, Capability<?> capability);

    /**
     * Removes every explicit value for a holder.
     *
     * @param holder the holder
     * @throws CapabilityException when the holder cannot be cleared
     */
    void clear(Object holder);
}
