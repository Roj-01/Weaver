package net.weaver.api.game.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Typed variant of {@link net.weaver.api.event.events.EntityJoinLevelEvent}.
 *
 * <p>The payloads are narrowed to the game types. The two-argument constructor
 * creates a cancelable event, matching the loader's entity-add hook; the
 * three-argument constructor lets the caller choose.
 */
public final class EntityJoinLevelEvent extends net.weaver.api.event.events.EntityJoinLevelEvent {

    /**
     * Creates a cancelable typed entity-join event.
     *
     * @param level  the server level the entity is being added to
     * @param entity the entity being added
     */
    public EntityJoinLevelEvent(Object level, Object entity) {
        super(level, entity, true);
    }

    /**
     * Creates a typed entity-join event with an explicit cancelable flag.
     *
     * @param level      the server level the entity is being added to
     * @param entity     the entity being added
     * @param cancelable whether listeners may cancel the addition
     */
    public EntityJoinLevelEvent(Object level, Object entity, boolean cancelable) {
        super(level, entity, cancelable);
    }

    @Override
    public ServerLevel level() {
        return (ServerLevel) super.level();
    }

    @Override
    public Entity entity() {
        return (Entity) super.entity();
    }
}
