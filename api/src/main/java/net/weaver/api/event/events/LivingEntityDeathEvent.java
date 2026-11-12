package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A living entity is about to die on the logical server.
 *
 * <p>Fired on the server thread at method entry, before
 * {@code LivingEntity#die}. Observation-only and not cancelable. This fires for
 * every living entity; when the dying entity is a player, a separate
 * {@link PlayerDeathEvent} may also fire. The base payloads are game objects
 * carried as {@link Object}; the typed {@code api-game} event exposes game
 * types.
 */
public class LivingEntityDeathEvent extends Event {

    private final Object entity;
    private final Object source;

    /**
     * Creates a living-entity-death event.
     *
     * @param entity the dying entity
     * @param source the damage source that killed the entity
     */
    public LivingEntityDeathEvent(Object entity, Object source) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.source = Objects.requireNonNull(source, "source");
    }

    /** The dying entity. */
    public Object entity() {
        return entity;
    }

    /** The damage source that killed the entity. */
    public Object source() {
        return source;
    }
}
