package net.weaver.api.game.event.living;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.event.Event;

/**
 * Fired when a living entity lands and fall damage is about to be applied
 * ({@code LivingEntity#causeFallDamage}). Cancelable: a canceled event deals no
 * fall damage. The distance and damage multiplier can be changed.
 */
public final class LivingFallEvent extends Event {
    private final LivingEntity entity;
    private final DamageSource source;
    private double distance;
    private float damageMultiplier;

    public LivingFallEvent(LivingEntity entity, double distance, float damageMultiplier,
                           DamageSource source) {
        super(true);
        this.entity = entity;
        this.distance = distance;
        this.damageMultiplier = damageMultiplier;
        this.source = source;
    }

    public LivingEntity entity() {
        return entity;
    }

    public DamageSource source() {
        return source;
    }

    public double distance() {
        return distance;
    }

    public void setDistance(double distance) {
        net.weaver.api.game.event.support.Finite.check(distance, "distance");
        this.distance = distance;
    }

    public float damageMultiplier() {
        return damageMultiplier;
    }

    public void setDamageMultiplier(float damageMultiplier) {
        net.weaver.api.game.event.support.Finite.check(damageMultiplier, "damageMultiplier");
        this.damageMultiplier = damageMultiplier;
    }
}
