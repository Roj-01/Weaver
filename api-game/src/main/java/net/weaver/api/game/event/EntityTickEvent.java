package net.weaver.api.game.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.weaver.api.event.Side;

/**
 * Typed variant of {@link net.weaver.api.event.events.EntityTickEvent}.
 *
 * <p>Fired at method entry to {@code ServerLevel#tickNonPassenger} or
 * {@code ClientLevel#tickNonPassenger}, before the entity's own tick runs.
 * Observation-only and not cancelable. The payloads are narrowed to the game
 * types. The inherited accessors stay available with their {@link Object}
 * descriptors for binary compatibility.
 */
public final class EntityTickEvent extends net.weaver.api.event.events.EntityTickEvent {

    /**
     * Creates a typed entity-tick event.
     *
     * @param level  the level ticking the entity
     * @param entity the ticking entity
     * @param side   the level side that ticks the entity
     */
    public EntityTickEvent(Object level, Object entity, Side side) {
        super(level, entity, side);
    }

    @Override
    public Level level() {
        return (Level) super.level();
    }

    @Override
    public Entity entity() {
        return (Entity) super.entity();
    }
}
