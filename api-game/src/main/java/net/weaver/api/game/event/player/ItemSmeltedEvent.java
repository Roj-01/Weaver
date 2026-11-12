package net.weaver.api.game.event.player;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.Event;

/**
 * Fired when a player takes a result from a furnace, smoker, or blast furnace
 * ({@code FurnaceResultSlot#onTake}).
 */
public final class ItemSmeltedEvent extends Event {
    private final Player player;
    private final ItemStack result;

    public ItemSmeltedEvent(Player player, ItemStack result) {
        this.player = player;
        this.result = result;
    }

    public Player player() {
        return player;
    }

    public ItemStack result() {
        return result;
    }
}
