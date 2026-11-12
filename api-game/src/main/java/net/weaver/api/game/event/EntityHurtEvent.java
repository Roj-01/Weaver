package net.weaver.api.game.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Typed variant of {@link net.weaver.api.event.events.EntityHurtEvent}.
 *
 * <p>Fired on the logical server at method entry to
 * {@code LivingEntity#hurtServer}, before damage is applied. The hook creates
 * the cancelable form; canceling makes {@code LivingEntity#hurtServer} return
 * {@code false} and skips damage for that call. The payloads are narrowed to
 * the game types. The inherited accessors stay available with their
 * {@link Object} descriptors for binary compatibility.
 */
public final class EntityHurtEvent extends net.weaver.api.event.events.EntityHurtEvent {

    /**
     * Creates an observation-only typed entity-hurt event.
     *
     * @param level  the server level
     * @param entity the entity being hurt
     * @param source the damage source
     */
    public EntityHurtEvent(Object level, Object entity, Object source) {
        this(level, entity, source, false);
    }

    /**
     * Creates a typed entity-hurt event with an explicit cancelable flag.
     *
     * <p>The hook creates the cancelable form; canceling makes
     * {@code LivingEntity#hurtServer} return {@code false} and skips damage for
     * that call.
     *
     * @param level      the server level
     * @param entity     the entity being hurt
     * @param source     the damage source
     * @param cancelable whether listeners may cancel the damage for this call
     */
    public EntityHurtEvent(Object level, Object entity, Object source, boolean cancelable) {
        super(level, entity, source, cancelable);
    }

    @Override
    public ServerLevel level() {
        return (ServerLevel) super.level();
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
