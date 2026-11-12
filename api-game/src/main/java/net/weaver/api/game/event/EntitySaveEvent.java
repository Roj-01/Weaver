package net.weaver.api.game.event;

import net.minecraft.world.entity.Entity;

/**
 * Typed variant of {@link net.weaver.api.event.events.EntitySaveEvent}.
 *
 * <p>Fired at method entry to {@code Entity#saveWithoutId(ValueOutput)V},
 * before the entity's own data is written. Observation-only and not cancelable.
 * The payload is narrowed to the game type. The inherited accessor stays
 * available with its {@link Object} descriptor for binary compatibility.
 */
public final class EntitySaveEvent extends net.weaver.api.event.events.EntitySaveEvent {

    /**
     * Creates a typed entity-save event.
     *
     * @param entity the entity saving its additional data
     */
    public EntitySaveEvent(Object entity) {
        super(entity);
    }

    @Override
    public Entity entity() {
        return (Entity) super.entity();
    }
}
