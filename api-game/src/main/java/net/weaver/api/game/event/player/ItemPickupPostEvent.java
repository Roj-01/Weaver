package net.weaver.api.game.event.player;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.Event;

/**
 * Fired on the server after a player picked up some or all of an item entity
 * ({@code ItemEntity#playerTouch}). {@link #original()} is a copy of the stack
 * before pickup; {@link #item()} holds what is left on the ground, which may
 * be empty. Not cancelable: the items are already in the inventory. To stop a
 * pickup use {@link ItemPickupEvent}.
 */
public final class ItemPickupPostEvent extends Event {
    private final Player player;
    private final ItemEntity item;
    private final ItemStack original;

    public ItemPickupPostEvent(Player player, ItemEntity item, ItemStack original) {
        this.player = player;
        this.item = item;
        this.original = original;
    }

    public Player player() {
        return player;
    }

    public ItemEntity item() {
        return item;
    }

    /** A copy of the stack before pickup. */
    public ItemStack original() {
        return original;
    }

    /** How many items went into the inventory. */
    public int pickedUp() {
        return original.getCount() - item.getItem().getCount();
    }
}
