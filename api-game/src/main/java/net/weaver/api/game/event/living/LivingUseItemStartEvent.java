package net.weaver.api.game.event.living;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.Event;

/**
 * Fired when a living entity starts using an item (eating, drinking, drawing a
 * bow, raising a shield; {@code LivingEntity#startUsingItem}). Cancelable: a
 * canceled event does not start the use. The duration in ticks can be changed.
 */
public final class LivingUseItemStartEvent extends Event {
    private final LivingEntity entity;
    private final ItemStack item;
    private final InteractionHand hand;
    private int duration;

    public LivingUseItemStartEvent(LivingEntity entity, ItemStack item, InteractionHand hand, int duration) {
        super(true);
        this.entity = entity;
        this.item = item;
        this.hand = hand;
        this.duration = duration;
    }

    public LivingEntity entity() {
        return entity;
    }

    public ItemStack item() {
        return item;
    }

    public InteractionHand hand() {
        return hand;
    }

    public int duration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }
}
