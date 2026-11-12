package net.weaver.api.game.event.living;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.event.Event;

/**
 * Fired when an item that blocks attacks (a shield) is about to block damage
 * ({@code LivingEntity#applyItemBlocking}, the {@code resolveBlockedDamage}
 * result). Cancelable: a canceled event blocks nothing. The blocked amount can
 * be changed.
 */
public final class LivingShieldBlockEvent extends Event {
    private final LivingEntity entity;
    private final DamageSource source;
    private final float damage;
    private float blockedDamage;

    public LivingShieldBlockEvent(LivingEntity entity, DamageSource source, float damage, float blockedDamage) {
        super(true);
        this.entity = entity;
        this.source = source;
        this.damage = damage;
        this.blockedDamage = blockedDamage;
    }

    public LivingEntity entity() {
        return entity;
    }

    public DamageSource source() {
        return source;
    }

    /** The incoming damage. */
    public float damage() {
        return damage;
    }

    public float blockedDamage() {
        return blockedDamage;
    }

    public void setBlockedDamage(float blockedDamage) {
        net.weaver.api.game.event.support.Finite.check(blockedDamage, "blockedDamage");
        this.blockedDamage = blockedDamage;
    }
}
