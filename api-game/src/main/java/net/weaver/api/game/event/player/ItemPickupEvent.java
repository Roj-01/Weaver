package net.weaver.api.game.event.player;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.event.Event;

/**
 * Fired on the server when a player touches an item entity and may pick it up
 * ({@code ItemEntity#playerTouch}). Cancelable: a canceled event leaves the item
 * on the ground for this tick.
 */
public final class ItemPickupEvent extends Event {
    private final Player player;
    private final ItemEntity item;

    public ItemPickupEvent(Player player, ItemEntity item) {
        super(true);
        this.player = player;
        this.item = item;
    }

    public Player player() {
        return player;
    }

    public ItemEntity item() {
        return item;
    }
}
