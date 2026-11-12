package net.weaver.api.game.event.living;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.event.Event;

/**
 * Fired when the game checks whether an effect can be applied to a living entity
 * ({@code LivingEntity#canBeAffected}). Cancelable: a canceled event makes the
 * entity immune to that effect for this check.
 */
public final class MobEffectApplicableEvent extends Event {
    private final LivingEntity entity;
    private final MobEffectInstance effect;

    public MobEffectApplicableEvent(LivingEntity entity, MobEffectInstance effect) {
        super(true);
        this.entity = entity;
        this.effect = effect;
    }

    public LivingEntity entity() {
        return entity;
    }

    public MobEffectInstance effect() {
        return effect;
    }
}
