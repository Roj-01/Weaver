package net.weaver.api.game.event.living;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.event.Event;

/**
 * Fired for each active effect when a living entity's effect particles are rebuilt
 * ({@code LivingEntity#updateSynchronizedMobEffectParticles}, when its effects change). The particle
 * can be replaced or hidden ({@code setVisible(false)}). Server side; clients get the result.
 */
public final class EffectParticleModificationEvent extends Event {
    private final LivingEntity entity;
    private final MobEffectInstance effect;
    private ParticleOptions options;
    private boolean visible;

    public EffectParticleModificationEvent(LivingEntity entity, MobEffectInstance effect, ParticleOptions options, boolean visible) {
        super(false);
        this.entity = entity;
        this.effect = effect;
        this.options = options;
        this.visible = visible;
    }

    /** The entity. */
    public LivingEntity entity() {
        return entity;
    }

    /** The effect. */
    public MobEffectInstance effect() {
        return effect;
    }

    /** The particle the effect shows. */
    public ParticleOptions options() {
        return options;
    }

    public void setOptions(ParticleOptions options) {
        this.options = options;
    }

    /** Whether the effect shows particles. */
    public boolean visible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}
