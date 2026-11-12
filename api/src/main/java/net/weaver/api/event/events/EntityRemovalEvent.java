package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * An entity is about to be removed.
 *
 * <p>Fired at method entry to {@code Entity#remove}, before the removal reason
 * is applied and the entity is marked removed. Observation-only and not
 * cancelable. The spec is installed on the logical server only, but
 * {@code Entity} is a shared class: in a process that hosts an integrated
 * client, client-side removals can also reach the injected body. Listeners
 * should check the logical context before mutating shared state.
 *
 * <p>The base payloads are game objects carried as {@link Object}; the typed
 * {@code api-game} event exposes game types.
 */
public class EntityRemovalEvent extends Event {

    private final Object entity;
    private final Object reason;

    /**
     * Creates an entity-removal event.
     *
     * @param entity the entity being removed
     * @param reason the removal reason
     */
    public EntityRemovalEvent(Object entity, Object reason) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /** The entity being removed. */
    public Object entity() {
        return entity;
    }

    /** The removal reason. */
    public Object reason() {
        return reason;
    }
}
