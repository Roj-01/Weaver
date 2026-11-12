package net.weaver.api.game.event.living;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.event.Event;

/**
 * Fired when a living entity is about to be hurt ({@code LivingEntity#hurtServer}),
 * before invulnerability, shield blocking, armor, and enchantments. The amount can
 * be changed (a combat mod's block or parry that takes part of a hit), and the
 * event can be canceled (no hurt at all, as with {@code EntityHurtEvent}).
 * For the damage after every reduction, use {@link LivingDamagePreEvent}.
 */
public final class LivingIncomingDamageEvent extends Event {
    private final LivingEntity entity;
    private final DamageSource source;
    private final float originalAmount;
    private float amount;

    public LivingIncomingDamageEvent(LivingEntity entity, DamageSource source, float amount) {
        super(true);
        this.entity = entity;
        this.source = source;
        this.originalAmount = amount;
        this.amount = amount;
    }

    public LivingEntity entity() {
        return entity;
    }

    public DamageSource source() {
        return source;
    }

    /** The amount before any listener changed it. */
    public float originalAmount() {
        return originalAmount;
    }

    public float amount() {
        return amount;
    }

    /** Sets the incoming amount; finite and not negative. */
    public void setAmount(float amount) {
        net.weaver.api.game.event.support.Finite.check(amount, "amount");
        this.amount = Math.max(0.0F, amount);
    }
}
