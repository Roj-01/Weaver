package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A living entity is about to be hurt on the logical server.
 *
 * <p>Fired on the logical server at method entry to
 * {@code LivingEntity#hurtServer}, before damage is applied. The hook creates
 * the cancelable form; canceling the event makes
 * {@code LivingEntity#hurtServer} return {@code false} and skips damage for
 * that call. The three-argument constructor remains observation-only for binary
 * compatibility.
 *
 * <p>The payloads are game objects carried as {@link Object} by the base API;
 * the typed {@code api-game} event exposes game types.
 */
public class EntityHurtEvent extends Event {

    private final Object level;
    private final Object entity;
    private final Object source;

    /**
     * Creates an observation-only entity-hurt event.
     *
     * @param level  the server level
     * @param entity the entity being hurt
     * @param source the damage source
     */
    public EntityHurtEvent(Object level, Object entity, Object source) {
        this(level, entity, source, false);
    }

    /**
     * Creates an entity-hurt event with an explicit cancelable flag.
     *
     * <p>The loader uses the cancelable form for the {@code hurtServer} hook.
     *
     * @param level      the server level
     * @param entity     the entity being hurt
     * @param source     the damage source
     * @param cancelable whether listeners may cancel the damage for this call
     */
    public EntityHurtEvent(Object level, Object entity, Object source, boolean cancelable) {
        super(cancelable);
        this.level = Objects.requireNonNull(level, "level");
        this.entity = Objects.requireNonNull(entity, "entity");
        this.source = Objects.requireNonNull(source, "source");
    }

    /** The server level. */
    public Object level() {
        return level;
    }

    /** The entity being hurt. */
    public Object entity() {
        return entity;
    }

    /** The damage source. */
    public Object source() {
        return source;
    }
}
