package net.weaver.api.game.event.living;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.event.Event;

/**
 * Fired when a living entity is about to be knocked back
 * ({@code LivingEntity#knockback}). Cancelable: a canceled event applies no
 * knockback. The strength and direction ratios can be changed.
 */
public final class LivingKnockBackEvent extends Event {
    private final LivingEntity entity;
    private final DamageSource source;
    private double strength;
    private double ratioX;
    private double ratioZ;

    public LivingKnockBackEvent(LivingEntity entity, double strength, double ratioX, double ratioZ,
                                DamageSource source) {
        super(true);
        this.entity = entity;
        this.strength = strength;
        this.ratioX = ratioX;
        this.ratioZ = ratioZ;
        this.source = source;
    }

    public LivingEntity entity() {
        return entity;
    }

    public DamageSource source() {
        return source;
    }

    public double strength() {
        return strength;
    }

    public void setStrength(double strength) {
        net.weaver.api.game.event.support.Finite.check(strength, "strength");
        this.strength = strength;
    }

    public double ratioX() {
        return ratioX;
    }

    public void setRatioX(double ratioX) {
        net.weaver.api.game.event.support.Finite.check(ratioX, "ratioX");
        this.ratioX = ratioX;
    }

    public double ratioZ() {
        return ratioZ;
    }

    public void setRatioZ(double ratioZ) {
        net.weaver.api.game.event.support.Finite.check(ratioZ, "ratioZ");
        this.ratioZ = ratioZ;
    }
}
