package net.weaver.api.game.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Typed variant of {@link net.weaver.api.event.events.LivingEntityDeathEvent}.
 *
 * <p>Fired on the server thread at method entry, before
 * {@code LivingEntity#die}. Observation-only and not cancelable. When the
 * dying entity is a player, a separate {@link PlayerDeathEvent} may also fire.
 * The payloads are narrowed to the game types. The inherited accessors stay
 * available with their {@link Object} descriptors for binary compatibility.
 */
public final class LivingEntityDeathEvent
        extends net.weaver.api.event.events.LivingEntityDeathEvent {

    /**
     * Creates a typed living-entity-death event.
     *
     * @param entity the dying entity
     * @param source the damage source that killed the entity
     */
    public LivingEntityDeathEvent(Object entity, Object source) {
        super(entity, source);
    }

    @Override
    public LivingEntity entity() {
        return (LivingEntity) super.entity();
    }

    @Override
    public DamageSource source() {
        return (DamageSource) super.source();
    }
}
