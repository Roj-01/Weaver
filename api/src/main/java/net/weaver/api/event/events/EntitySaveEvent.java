package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * An entity is about to save its additional data.
 *
 * <p>Fired at method entry to {@code Entity#saveWithoutId(ValueOutput)V},
 * before the entity's own data is written. The hook targets the concrete
 * wrapper because {@code Entity#addAdditionalSaveData(ValueOutput)V} is
 * abstract in 26.3 and therefore has no method body to instrument;
 * {@code saveWithoutId} calls that abstract method for every entity subclass.
 * Observation-only and not cancelable. The spec is installed on the logical
 * server only. The base class body is instrumented; a subclass override that
 * does not call {@code super} bypasses the hook.</p>
 *
 * <p>The event is posted before Weaver writes its own capability root, so a
 * listener can adjust attachment values before they are persisted.</p>
 *
 * <p>The base payload is a game object carried as {@link Object}; the typed
 * {@code api-game} event exposes game types.</p>
 */
public class EntitySaveEvent extends Event {

    private final Object entity;

    /**
     * Creates an entity-save event.
     *
     * @param entity the entity saving its additional data
     */
    public EntitySaveEvent(Object entity) {
        this.entity = Objects.requireNonNull(entity, "entity");
    }

    /** The entity saving its additional data. */
    public Object entity() {
        return entity;
    }
}
