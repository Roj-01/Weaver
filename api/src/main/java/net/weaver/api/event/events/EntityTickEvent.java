package net.weaver.api.event.events;

import net.weaver.api.event.Event;
import net.weaver.api.event.Side;

import java.util.Objects;

/**
 * An entity is ticking in a level.
 *
 * <p>Fired at method entry to {@code ServerLevel#tickNonPassenger} on the
 * logical server and {@code ClientLevel#tickNonPassenger} on the logical
 * client, before the entity's own tick runs. The same event class is used for
 * both sides; read {@link #side()} to distinguish them. Passenger entities are
 * ticked through a different path and do not fire this event.
 *
 * <p>Observation-only and not cancelable. The base payloads are game objects
 * carried as {@link Object}; the typed {@code api-game} event exposes game
 * types.
 */
public class EntityTickEvent extends Event {

    private final Object level;
    private final Object entity;
    private final Side side;

    /**
     * Creates an entity-tick event.
     *
     * @param level  the level ticking the entity
     * @param entity the ticking entity
     * @param side   the level side that ticks the entity
     */
    public EntityTickEvent(Object level, Object entity, Side side) {
        this.level = Objects.requireNonNull(level, "level");
        this.entity = Objects.requireNonNull(entity, "entity");
        this.side = Objects.requireNonNull(side, "side");
    }

    /** The level ticking the entity. */
    public Object level() {
        return level;
    }

    /** The ticking entity. */
    public Object entity() {
        return entity;
    }

    /** The side of the level that ticks the entity. */
    public Side side() {
        return side;
    }
}
