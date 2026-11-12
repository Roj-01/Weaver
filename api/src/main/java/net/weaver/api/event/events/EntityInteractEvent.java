package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A player is about to interact with an entity.
 *
 * <p>Fired at method entry to {@code Player#interactOn}, before the
 * interaction is resolved. The spec is installed on the logical server only,
 * but {@code Player} is a shared class: in a process that hosts an integrated
 * client, client-side invocations can also reach the injected body. Listeners
 * should check the logical context before mutating shared state.
 * Observation-only and not cancelable. The base payloads are game objects
 * carried as {@link Object}; the typed {@code api-game} event exposes game
 * types.
 */
public class EntityInteractEvent extends Event {

    private final Object player;
    private final Object entity;
    private final Object hand;
    private final Object hitVec;

    /**
     * Creates an entity-interaction event.
     *
     * @param player the interacting player
     * @param entity the entity being interacted with
     * @param hand   the hand used for the interaction
     * @param hitVec the interaction position
     */
    public EntityInteractEvent(Object player, Object entity, Object hand, Object hitVec) {
        this.player = Objects.requireNonNull(player, "player");
        this.entity = Objects.requireNonNull(entity, "entity");
        this.hand = Objects.requireNonNull(hand, "hand");
        this.hitVec = Objects.requireNonNull(hitVec, "hitVec");
    }

    /** The interacting player. */
    public Object player() {
        return player;
    }

    /** The entity being interacted with. */
    public Object entity() {
        return entity;
    }

    /** The hand used for the interaction. */
    public Object hand() {
        return hand;
    }

    /** The interaction position. */
    public Object hitVec() {
        return hitVec;
    }
}
