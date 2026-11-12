package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A player is about to attack an entity.
 *
 * <p>Fired at method entry to {@code Player#attack}, before the attack is
 * resolved. The hook creates the cancelable form; canceling the event makes
 * {@code Player#attack} return immediately and skips the attack body for that
 * call. The spec is installed on the logical server only, but {@code Player}
 * is a shared class: in a process that hosts an integrated client, client-side
 * invocations can also reach the injected body. Listeners should check the
 * logical context before mutating shared state.
 *
 * <p>The two-argument constructor is observation-only. The base payloads are
 * game objects carried as {@link Object}; the typed {@code api-game} event
 * exposes game types.
 */
public class EntityAttackEvent extends Event {

    private final Object player;
    private final Object target;

    /**
     * Creates an observation-only entity-attack event.
     *
     * @param player the attacking player
     * @param target the entity being attacked
     */
    public EntityAttackEvent(Object player, Object target) {
        this(player, target, false);
    }

    /**
     * Creates an entity-attack event with an explicit cancelable flag.
     *
     * <p>The hook uses the cancelable form for the {@code Player#attack}
     * injection point.
     *
     * @param player     the attacking player
     * @param target     the entity being attacked
     * @param cancelable whether listeners may cancel the attack for this call
     */
    public EntityAttackEvent(Object player, Object target, boolean cancelable) {
        super(cancelable);
        this.player = Objects.requireNonNull(player, "player");
        this.target = Objects.requireNonNull(target, "target");
    }

    /** The attacking player. */
    public Object player() {
        return player;
    }

    /** The entity being attacked. */
    public Object target() {
        return target;
    }
}
