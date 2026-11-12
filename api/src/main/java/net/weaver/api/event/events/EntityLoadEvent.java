package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * An entity is about to load its additional data.
 *
 * <p>Fired at method entry to {@code Entity#load(ValueInput)V}, after Weaver
 * restores its capability root and before the entity's own data is applied.
 * The hook targets the concrete wrapper because
 * {@code Entity#readAdditionalSaveData(ValueInput)V} is abstract in 26.3 and
 * therefore has no method body to instrument; {@code load} calls that abstract
 * method for every entity subclass. Observation-only and not cancelable. The
 * spec is installed on the logical server only. The base class body is
 * instrumented; a subclass override that does not call {@code super} bypasses
 * the hook.</p>
 *
 * <p>The event is posted after Weaver restores its capability values, so a
 * listener observes the restored state.</p>
 *
 * <p>The base payload is a game object carried as {@link Object}; the typed
 * {@code api-game} event exposes game types.</p>
 */
public class EntityLoadEvent extends Event {

    private final Object entity;

    /**
     * Creates an entity-load event.
     *
     * @param entity the entity loading its additional data
     */
    public EntityLoadEvent(Object entity) {
        this.entity = Objects.requireNonNull(entity, "entity");
    }

    /** The entity loading its additional data. */
    public Object entity() {
        return entity;
    }
}
