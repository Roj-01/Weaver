package net.weaver.api.game.event.player;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.Event;

/**
 * Fired when a player takes a crafting result ({@code ResultSlot#onTake}), before
 * the ingredients are consumed.
 */
public final class ItemCraftedEvent extends Event {
    private final Player player;
    private final ItemStack result;
    private final Container craftingGrid;

    public ItemCraftedEvent(Player player, ItemStack result, Container craftingGrid) {
        this.player = player;
        this.result = result;
        this.craftingGrid = craftingGrid;
    }

    public Player player() {
        return player;
    }

    /** The crafted stack being taken. */
    public ItemStack result() {
        return result;
    }

    /** The crafting grid holding the ingredients. */
    public Container craftingGrid() {
        return craftingGrid;
    }
}
