package net.weaver.api.game.event.living;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.Event;

/**
 * Fired every tick while a living entity uses an item
 * ({@code LivingEntity#updateUsingItem}). The remaining ticks can be changed;
 * zero or less finishes the use on this tick.
 */
public final class LivingUseItemTickEvent extends Event {
    private final LivingEntity entity;
    private final ItemStack item;
    private int remaining;

    public LivingUseItemTickEvent(LivingEntity entity, ItemStack item, int remaining) {
        this.entity = entity;
        this.item = item;
        this.remaining = remaining;
    }

    public LivingEntity entity() {
        return entity;
    }

    public ItemStack item() {
        return item;
    }

    public int remaining() {
        return remaining;
    }

    public void setRemaining(int remaining) {
        this.remaining = remaining;
    }
}
