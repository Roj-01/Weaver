package net.weaver.api.game.event.player;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.Event;

/**
 * Fired after a player took the result out of an anvil and vanilla charged
 * the cost, used up the inputs, and maybe damaged the anvil
 * ({@code AnvilMenu#onTake}). {@link #left()} and {@link #right()} are copies
 * of the inputs from before the craft.
 */
public final class AnvilCraftPostEvent extends Event {
    private final Player player;
    private final AnvilMenu menu;
    private final ItemStack output;
    private final ItemStack left;
    private final ItemStack right;

    public AnvilCraftPostEvent(Player player, AnvilMenu menu, ItemStack output, ItemStack left, ItemStack right) {
        this.player = player;
        this.menu = menu;
        this.output = output;
        this.left = left;
        this.right = right;
    }

    public Player player() {
        return player;
    }

    public AnvilMenu menu() {
        return menu;
    }

    public ItemStack output() {
        return output;
    }

    /** A copy of the left input before the craft. */
    public ItemStack left() {
        return left;
    }

    /** A copy of the right input before the craft. */
    public ItemStack right() {
        return right;
    }
}
