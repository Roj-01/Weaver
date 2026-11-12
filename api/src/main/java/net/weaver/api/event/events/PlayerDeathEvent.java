package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A server player is dying. Observation-only; the base payloads are game
 * objects carried as {@link Object}.
 */
public class PlayerDeathEvent extends Event {

    private final Object player;
    private final Object source;

    /**
     * Creates a player-death event.
     *
     * @param player the dying player
     * @param source the damage source that killed the player
     */
    public PlayerDeathEvent(Object player, Object source) {
        this.player = Objects.requireNonNull(player, "player");
        this.source = Objects.requireNonNull(source, "source");
    }

    /** The dying player. */
    public Object player() {
        return player;
    }

    /** The damage source. */
    public Object source() {
        return source;
    }
}
