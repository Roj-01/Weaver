package net.weaver.api.game.event.living;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.event.Event;

/**
 * Fired after an effect was added to, or updated on, a living entity
 * ({@code LivingEntity#addEffect} returned {@code true}).
 */
public final class MobEffectAddedEvent extends Event {
    private final LivingEntity entity;
    private final MobEffectInstance effect;
    private final Entity source;

    public MobEffectAddedEvent(LivingEntity entity, MobEffectInstance effect, Entity source) {
        this.entity = entity;
        this.effect = effect;
        this.source = source;
    }

    public LivingEntity entity() {
        return entity;
    }

    public MobEffectInstance effect() {
        return effect;
    }

    /** The entity that applied the effect, or {@code null}. */
    public Entity source() {
        return source;
    }
}
