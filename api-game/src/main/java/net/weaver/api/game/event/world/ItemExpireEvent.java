package net.weaver.api.game.event.world;

import net.minecraft.world.entity.item.ItemEntity;
import net.weaver.api.event.Event;

/**
 * Fired on the server when a dropped item reaches the end of its lifetime
 * (five minutes, {@code ItemEntity#tick}) and is about to despawn. Set
 * {@link #setExtraLife(int)} to a positive number of ticks to keep it that
 * much longer; the event fires again when that time runs out.
 */
public final class ItemExpireEvent extends Event {
    private final ItemEntity item;
    private int extraLife;

    public ItemExpireEvent(ItemEntity item) {
        this.item = item;
    }

    public ItemEntity item() {
        return item;
    }

    public int extraLife() {
        return extraLife;
    }

    /** Ticks to add; {@code 0} or less lets the item despawn. */
    public void setExtraLife(int extraLife) {
        this.extraLife = extraLife;
    }
}
