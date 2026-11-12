package net.weaver.api.game.event.player;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.Event;

/**
 * Fired when a player tries to take the result out of an anvil
 * ({@code AnvilMenu#mayPickup}, after vanilla allowed it), before the result
 * leaves the slot. Cancelable: a canceled event refuses the take, so nothing
 * is charged and nothing is taken. It can fire more than once per take.
 */
public final class AnvilCraftPreEvent extends Event {
    private final Player player;
    private final AnvilMenu menu;
    private final ItemStack output;
    private final ItemStack left;
    private final ItemStack right;

    public AnvilCraftPreEvent(Player player, AnvilMenu menu, ItemStack output, ItemStack left, ItemStack right) {
        super(true);
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

    /** The stack the player took. */
    public ItemStack output() {
        return output;
    }

    /** The left input, still in its slot. */
    public ItemStack left() {
        return left;
    }

    /** The right input (material or book), still in its slot. */
    public ItemStack right() {
        return right;
    }
}
