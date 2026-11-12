package net.weaver.api.game.event.living;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.event.Event;

/**
 * Fired after an effect ran out on a living entity and was removed
 * ({@code LivingEntity#tickEffects}). Not cancelable; to keep an effect, add it
 * again or use {@code MobEffectRemoveEvent} for removals by milk and commands.
 */
public final class MobEffectExpiredEvent extends Event {
    private final LivingEntity entity;
    private final MobEffectInstance effect;

    public MobEffectExpiredEvent(LivingEntity entity, MobEffectInstance effect) {
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
