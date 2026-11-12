package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A server player is about to teleport.
 *
 * <p>Fired on the server thread at method entry, before
 * {@code ServerPlayer#teleport}. The teleport may be same-dimension or
 * cross-dimension, so listeners that care about the destination compare the
 * source and destination levels from the transition payload. Observation-only
 * and not cancelable. The base payloads are game objects carried as
 * {@link Object}; the typed {@code api-game} event exposes game types.
 */
public class PlayerTeleportEvent extends Event {

    private final Object player;
    private final Object transition;

    /**
     * Creates a player-teleport event.
     *
     * @param player     the teleporting player
     * @param transition the transition describing the teleport
     */
    public PlayerTeleportEvent(Object player, Object transition) {
        this.player = Objects.requireNonNull(player, "player");
        this.transition = Objects.requireNonNull(transition, "transition");
    }

    /** The teleporting player. */
    public Object player() {
        return player;
    }

    /** The transition describing the teleport. */
    public Object transition() {
        return transition;
    }
}
