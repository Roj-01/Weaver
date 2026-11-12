package net.weaver.api.game.lookup;

import java.util.Objects;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;

/**
 * An {@link ItemHandler} over a vanilla {@link Container}. With a side and a
 * {@link WorldlyContainer} (furnaces, brewing stands, composters) only the
 * slots that side exposes are visible, and the container's per-side insert
 * and extract rules apply, as they do for hoppers.
 */
public final class ContainerItemHandler implements ItemHandler {
    private final Container container;
    private final Direction side;
    private final int[] slots;

    private ContainerItemHandler(Container container, Direction side, int[] slots) {
        this.container = container;
        this.side = side;
        this.slots = slots;
    }

    /** Every slot of {@code container}. */
    public static ContainerItemHandler of(Container container) {
        return of(container, null);
    }

    /** The slots of {@code container} reachable from {@code side} ({@code null}: every slot). */
    public static ContainerItemHandler of(Container container, Direction side) {
        Objects.requireNonNull(container, "container");
        int[] slots;
        if (side != null && container instanceof WorldlyContainer worldly) {
            slots = worldly.getSlotsForFace(side).clone();
        } else {
            slots = new int[container.getContainerSize()];
            for (int slot = 0; slot < slots.length; slot++) {
                slots[slot] = slot;
            }
        }
        return new ContainerItemHandler(container, side, slots);
    }

    /** The wrapped container. */
    public Container container() {
        return container;
    }

    @Override
    public int slots() {
        return slots.length;
    }

    @Override
    public ItemStack stack(int slot) {
        return inRange(slot) ? container.getItem(slots[slot]) : ItemStack.EMPTY;
    }

    @Override
    public int limit(int slot) {
        return container.getMaxStackSize();
    }

    @Override
    public boolean canPlace(int slot, ItemStack stack) {
        if (!inRange(slot)) {
            return false;
        }
        int index = slots[slot];
        if (!container.canPlaceItem(index, stack)) {
            return false;
        }
        return side == null || !(container instanceof WorldlyContainer worldly)
                || worldly.canPlaceItemThroughFace(index, stack, side);
    }

    @Override
    public ItemStack insert(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!canPlace(slot, stack)) {
            return stack;
        }
        int index = slots[slot];
        ItemStack existing = container.getItem(index);
        int max = Math.min(container.getMaxStackSize(stack), stack.getMaxStackSize());
        int room;
        if (existing.isEmpty()) {
            room = max;
        } else if (ItemStack.isSameItemSameComponents(existing, stack)) {
            room = max - existing.getCount();
        } else {
            return stack;
        }
        if (room <= 0) {
            return stack;
        }
        int moved = Math.min(room, stack.getCount());
        if (!simulate) {
            container.setItem(index, stack.copyWithCount(existing.isEmpty() ? moved : existing.getCount() + moved));
            container.setChanged();
        }
        return moved == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
    }

    @Override
    public ItemStack extract(int slot, int amount, boolean simulate) {
        if (amount <= 0 || !inRange(slot)) {
            return ItemStack.EMPTY;
        }
        int index = slots[slot];
        ItemStack existing = container.getItem(index);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (side != null && container instanceof WorldlyContainer worldly
                && !worldly.canTakeItemThroughFace(index, existing, side)) {
            return ItemStack.EMPTY;
        }
        int moved = Math.min(amount, Math.min(existing.getCount(), existing.getMaxStackSize()));
        if (simulate) {
            return existing.copyWithCount(moved);
        }
        ItemStack taken = container.removeItem(index, moved);
        container.setChanged();
        return taken;
    }

    private boolean inRange(int slot) {
        return slot >= 0 && slot < slots.length;
    }
}
