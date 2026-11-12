package net.weaver.api.game.event.living;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.event.Event;

/**
 * Fired after damage was applied to a living entity ({@code actuallyHurt}
 * returned). {@link #damage()} is the amount after reductions and listeners;
 * {@link #healthLost()} is how much health actually went down (absorption
 * hearts are not health).
 */
public final class LivingDamagePostEvent extends Event {
    private final LivingEntity entity;
    private final DamageSource source;
    private final float damage;
    private final float healthLost;

    public LivingDamagePostEvent(LivingEntity entity, DamageSource source, float damage, float healthLost) {
        this.entity = entity;
        this.source = source;
        this.damage = damage;
        this.healthLost = healthLost;
    }

    public LivingEntity entity() {
        return entity;
    }

    public DamageSource source() {
        return source;
    }

    public float damage() {
        return damage;
    }

    public float healthLost() {
        return healthLost;
    }
}
