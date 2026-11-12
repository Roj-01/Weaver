package net.weaver.api.game.event.living;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.event.Event;

/**
 * Fired when an effect is about to be removed from a living entity
 * ({@code LivingEntity#removeEffect}, for example by milk or a command).
 * Cancelable: a canceled event keeps the effect. Natural expiry does not fire
 * this event.
 */
public final class MobEffectRemoveEvent extends Event {
    private final LivingEntity entity;
    private final Holder<MobEffect> effect;

    public MobEffectRemoveEvent(LivingEntity entity, Holder<MobEffect> effect) {
        super(true);
        this.entity = entity;
        this.effect = effect;
    }

    public LivingEntity entity() {
        return entity;
    }

    public Holder<MobEffect> effect() {
        return effect;
    }
}
