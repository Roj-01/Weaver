package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

public class EntityJoinLevelEvent extends Event
{
    private final Object level;
    private final Object entity;

    public EntityJoinLevelEvent(Object level, Object entity)
    {
        this(level, entity, false);
    }

    /**
     * Creates an entity-join event with an explicit cancelable flag.
     *
     * <p>The loader uses the cancelable form for the {@code addFreshEntity}
     * hook; the two-argument constructor remains observation-only for binary
     * compatibility.
     *
     * @param level      the level the entity is being added to
     * @param entity     the entity being added
     * @param cancelable whether listeners may cancel the addition
     */
    public EntityJoinLevelEvent(Object level, Object entity, boolean cancelable)
    {
        super(cancelable);
        this.level = Objects.requireNonNull(level, "level");
        this.entity = Objects.requireNonNull(entity, "entity");
    }

    public Object level()
    {
        return level;
    }

    public Object entity()
    {
        return entity;
    }
}
