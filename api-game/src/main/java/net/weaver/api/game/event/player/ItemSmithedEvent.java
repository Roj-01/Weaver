package net.weaver.api.game.event.player;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.Event;

/**
 * Fired when a player takes the result out of a smithing table
 * ({@code SmithingMenu#onTake}), before the template, base, and addition are
 * used up. Not fired for shift-click crafting, where vanilla passes an empty
 * stack. Not cancelable.
 */
public final class ItemSmithedEvent extends Event {
    private final Player player;
    private final SmithingMenu menu;
    private final ItemStack result;

    public ItemSmithedEvent(Player player, SmithingMenu menu, ItemStack result) {
        this.player = player;
        this.menu = menu;
        this.result = result;
    }

    public Player player() {
        return player;
    }

    /** The menu; slots 0, 1, and 2 hold the template, base, and addition. */
    public SmithingMenu menu() {
        return menu;
    }

    public ItemStack result() {
        return result;
    }
}
