package net.weaver.api.game.lookup;

import net.minecraft.world.item.ItemStack;

/**
 * Slots of item stacks that other code can insert into and extract from, for
 * moving items between machines of different mods.
 *
 * <p>Every change goes through {@link #insert} and {@link #extract}; with
 * {@code simulate} they report what would happen and change nothing. Never
 * modify a stack returned by {@link #stack}, and never keep a stack passed to
 * {@link #insert}: the handler copies what it takes.</p>
 */
public interface ItemHandler {
    /** The number of slots. */
    int slots();

    /** The stack in {@code slot}, or {@link ItemStack#EMPTY}. Read only. */
    ItemStack stack(int slot);

    /**
     * Inserts as much of {@code stack} into {@code slot} as fits.
     *
     * @return what did not fit ({@link ItemStack#EMPTY} when everything went in)
     */
    ItemStack insert(int slot, ItemStack stack, boolean simulate);

    /**
     * Extracts up to {@code amount} items from {@code slot}.
     *
     * @return what was (or would be) taken, possibly {@link ItemStack#EMPTY}
     */
    ItemStack extract(int slot, int amount, boolean simulate);

    /** The most items {@code slot} can hold, before the item's own stack limit. */
    int limit(int slot);

    /** Whether {@code stack} may go into {@code slot} at all. */
    default boolean canPlace(int slot, ItemStack stack) {
        return true;
    }

    /**
     * Inserts {@code stack} into the slots in order, first topping up slots
     * that already hold the same item.
     *
     * @return what did not fit
     */
    default ItemStack insertAnywhere(ItemStack stack, boolean simulate) {
        ItemStack remaining = stack;
        for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
            for (int slot = 0; slot < slots() && !remaining.isEmpty(); slot++) {
                boolean occupied = !stack(slot).isEmpty();
                if ((pass == 0) == occupied) {
                    remaining = insert(slot, remaining, simulate);
                }
            }
        }
        return remaining;
    }
}
