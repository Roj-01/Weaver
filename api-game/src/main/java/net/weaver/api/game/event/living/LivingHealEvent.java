package net.weaver.api.game.event.living;

import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.event.Event;

/**
 * Fired when a living entity is about to heal ({@code LivingEntity#heal}).
 * Cancelable: a canceled event heals nothing. The amount can be changed; an
 * amount of zero or less also heals nothing.
 */
public final class LivingHealEvent extends Event {
    private final LivingEntity entity;
    private float amount;

    public LivingHealEvent(LivingEntity entity, float amount) {
        super(true);
        this.entity = entity;
        this.amount = amount;
    }

    public LivingEntity entity() {
        return entity;
    }

    public float amount() {
        return amount;
    }

    public void setAmount(float amount) {
        net.weaver.api.game.event.support.Finite.check(amount, "amount");
        this.amount = amount;
    }
}
