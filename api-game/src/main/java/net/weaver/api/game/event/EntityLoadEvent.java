package net.weaver.api.game.event;

import net.minecraft.world.entity.Entity;

/**
 * Typed variant of {@link net.weaver.api.event.events.EntityLoadEvent}.
 *
 * <p>Fired at method entry to {@code Entity#load(ValueInput)V}, after Weaver
 * restores its capability root. Observation-only and not cancelable. The
 * payload is narrowed to the game type. The inherited accessor stays available
 * with its {@link Object} descriptor for binary compatibility.
 */
public final class EntityLoadEvent extends net.weaver.api.event.events.EntityLoadEvent {

    /**
     * Creates a typed entity-load event.
     *
     * @param entity the entity loading its additional data
     */
    public EntityLoadEvent(Object entity) {
        super(entity);
    }

    @Override
    public Entity entity() {
        return (Entity) super.entity();
    }
}
