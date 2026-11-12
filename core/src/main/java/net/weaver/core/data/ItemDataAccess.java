package net.weaver.core.data;

/**
 * Low-level byte access to Weaver capability data stored on a game item stack.
 *
 * <p>Values live in the nested compound at {@link ItemDataLimits#ROOT_KEY}
 * inside the {@code minecraft:custom_data} data component. Only that nested
 * compound is touched: other custom-data keys and all other data components
 * are preserved.</p>
 *
 * <p>Holders must be game {@code net.minecraft.world.item.ItemStack}
 * instances. Reads return defensive copies of stored byte arrays and writes
 * store a defensive copy of the supplied array. Implementations enforce the
 * hard bounds in {@link ItemDataLimits}.</p>
 *
 * <p>Implementations are not required to be thread-safe.</p>
 */
public interface ItemDataAccess
{
    /**
     * Returns whether this implementation can store capability data on the
     * given holder.
     *
     * @param holder candidate holder, possibly {@code null}
     * @return {@code true} only for a supported game item stack
     */
    boolean supports(Object holder);

    /**
     * Returns whether the holder is an empty game item stack.
     *
     * @param holder supported item stack
     * @return {@code true} when the game reports the stack as empty
     * @throws ItemDataException when the holder is not supported
     */
    boolean isEmpty(Object holder);

    /**
     * Reads the raw capability bytes stored under a key.
     *
     * @param holder supported item stack
     * @param key    capability key inside the Weaver root compound
     * @return a defensive copy of the stored bytes, or {@code null} when absent
     * @throws ItemDataException        when the holder is unsupported or the
     *                                  game classes cannot be reached
     * @throws IllegalArgumentException when {@code key} is {@code null}
     */
    byte[] read(Object holder, String key);

    /**
     * Returns whether the holder has capability bytes stored under a key.
     *
     * @param holder supported item stack
     * @param key    capability key inside the Weaver root compound
     * @return {@code true} when the key is present in the Weaver root compound
     * @throws ItemDataException        when the holder is unsupported or the
     *                                  game classes cannot be reached
     * @throws IllegalArgumentException when {@code key} is {@code null}
     */
    boolean contains(Object holder, String key);

    /**
     * Stores a defensive copy of the supplied bytes under a key, replacing any
     * previous value.
     *
     * @param holder supported item stack
     * @param key    capability key inside the Weaver root compound
     * @param data   encoded capability value; copied before storage
     * @throws ItemDataException        when the holder is unsupported, a limit
     *                                  would be exceeded, or the game classes
     *                                  cannot be reached
     * @throws IllegalArgumentException when {@code key} or {@code data} is
     *                                  {@code null}
     */
    void write(Object holder, String key, byte[] data);

    /**
     * Removes the value stored under a key.
     *
     * @param holder supported item stack
     * @param key    capability key inside the Weaver root compound
     * @return a defensive copy of the removed bytes, or {@code null} when absent
     * @throws ItemDataException        when the holder is unsupported or the
     *                                  game classes cannot be reached
     * @throws IllegalArgumentException when {@code key} is {@code null}
     */
    byte[] remove(Object holder, String key);

    /**
     * Removes the entire Weaver root compound from the holder while preserving
     * every other custom-data key and data component.
     *
     * @param holder supported item stack
     * @throws ItemDataException when the holder is unsupported or the game
     *                           classes cannot be reached
     */
    void clear(Object holder);

    /**
     * Returns the number of entries in the Weaver root compound.
     *
     * @param holder supported item stack
     * @return entry count, or {@code 0} when no Weaver root compound is present
     * @throws ItemDataException when the holder is unsupported or the game
     *                           classes cannot be reached
     */
    int entryCount(Object holder);

    /**
     * Returns the NBT size of the Weaver root compound.
     *
     * <p>The default implementation returns {@code -1} without inspecting the
     * holder. Implementations that can report the size override this method.</p>
     *
     * @param holder supported item stack
     * @return root compound size in bytes, or {@code -1} when the size is
     *         unavailable
     * @throws ItemDataException when the holder is unsupported
     */
    default int totalBytes(Object holder)
    {
        return -1;
    }
}
