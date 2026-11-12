package net.weaver.api.game.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;

/**
 * Typed variant of {@link net.weaver.api.event.events.EntityRemovalEvent}.
 *
 * <p>Fired at method entry to {@code Entity#remove}, before the removal reason
 * is applied. Observation-only and not cancelable. The payloads are narrowed
 * to the game types. The inherited accessors stay available with their
 * {@link Object} descriptors for binary compatibility.
 */
public final class EntityRemovalEvent extends net.weaver.api.event.events.EntityRemovalEvent {

    /**
     * Creates a typed entity-removal event.
     *
     * @param entity the entity being removed
     * @param reason the removal reason
     */
    public EntityRemovalEvent(Object entity, Object reason) {
        super(entity, reason);
    }

    @Override
    public Entity entity() {
        return (Entity) super.entity();
    }

    @Override
    public RemovalReason reason() {
        return (RemovalReason) super.reason();
    }
}
