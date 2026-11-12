package net.weaver.api.game.event.living;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.event.Event;

/**
 * Fired when damage to a living entity has passed armor and enchantment
 * reductions and is about to be applied ({@code actuallyHurt} of
 * {@code LivingEntity} and {@code Player}). Absorption hearts are taken after
 * this. Cancelable: a canceled event deals no damage. The damage can be changed.
 * For cancelling the whole hit before any reduction, use {@code EntityHurtEvent}.
 */
public final class LivingDamagePreEvent extends Event {
    private final LivingEntity entity;
    private final DamageSource source;
    private final float originalDamage;
    private float damage;

    public LivingDamagePreEvent(LivingEntity entity, DamageSource source, float damage) {
        super(true);
        this.entity = entity;
        this.source = source;
        this.originalDamage = damage;
        this.damage = damage;
    }

    public LivingEntity entity() {
        return entity;
    }

    public DamageSource source() {
        return source;
    }

    /** The damage after reductions, before any listener changed it. */
    public float originalDamage() {
        return originalDamage;
    }

    public float damage() {
        return damage;
    }

    public void setDamage(float damage) {
        net.weaver.api.game.event.support.Finite.check(damage, "damage");
        this.damage = damage;
    }
}
