package net.weaver.api.game.event.player;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.weaver.api.event.Event;

/**
 * Fired after a player dropped an item ({@code ServerPlayer#drop} created an
 * item entity). Listeners may change the item entity, for example its pickup
 * delay or motion. Not cancelable: the stack has already left the inventory.
 */
public final class ItemTossEvent extends Event {
    private final ServerPlayer player;
    private final ItemEntity item;

    public ItemTossEvent(ServerPlayer player, ItemEntity item) {
        this.player = player;
        this.item = item;
    }

    public ServerPlayer player() {
        return player;
    }

    public ItemEntity item() {
        return item;
    }
}
