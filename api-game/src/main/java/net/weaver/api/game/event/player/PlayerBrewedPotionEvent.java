package net.weaver.api.game.event.player;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.Event;

/**
 * Fired on the server when a player takes a potion out of a brewing stand
 * (the potion slot's {@code onTake}). Not cancelable: the stack is already
 * on the cursor.
 */
public final class PlayerBrewedPotionEvent extends Event {
    private final Player player;
    private final ItemStack potion;

    public PlayerBrewedPotionEvent(Player player, ItemStack potion) {
        this.player = player;
        this.potion = potion;
    }

    public Player player() {
        return player;
    }

    public ItemStack potion() {
        return potion;
    }
}
