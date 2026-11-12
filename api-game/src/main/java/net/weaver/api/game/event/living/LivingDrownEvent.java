package net.weaver.api.game.event.living;

import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.event.Event;

/**
 * Fired when a living entity out of air is about to take drowning damage
 * ({@code LivingEntity#baseTick}). Cancelable: a canceled event deals no
 * drowning damage this time.
 */
public final class LivingDrownEvent extends Event {
    private final LivingEntity entity;
    private final float damage;

    public LivingDrownEvent(LivingEntity entity, float damage) {
        super(true);
        this.entity = entity;
        this.damage = damage;
    }

    public LivingEntity entity() {
        return entity;
    }

    public float damage() {
        return damage;
    }
}
