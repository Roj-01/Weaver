package net.weaver.api.event.events;

import net.weaver.api.event.Event;
import net.weaver.api.event.Side;

import java.util.Objects;

/**
 * A client local player is ticking.
 *
 * <p>Fired at method entry to and normal exit from {@code LocalPlayer#tick}.
 * The client-side counterpart of the server-gated {@link PlayerTickEvent}. The
 * event is not cancelable and carries no tick counter; use {@link #phase()} to
 * distinguish {@link Phase#PRE} from {@link Phase#POST}.
 *
 * <p>The base payload is a game object carried as {@link Object}; the typed
 * {@code api-game} event narrows it to the client player type.
 */
public abstract class ClientPlayerTickEvent extends Event {

    /** The tick phase reported by the event. */
    public enum Phase {
        /** The local player tick is starting. */
        PRE,
        /** The local player tick finished normally. */
        POST
    }

    private final Object player;
    private final Phase phase;

    /**
     * Creates a client-player-tick event.
     *
     * @param player the ticking local player
     * @param phase  the tick phase
     */
    protected ClientPlayerTickEvent(Object player, Phase phase) {
        this.player = Objects.requireNonNull(player, "player");
        this.phase = Objects.requireNonNull(phase, "phase");
    }

    /** The ticking local player. */
    public Object player() {
        return player;
    }

    /** The tick phase. */
    public Phase phase() {
        return phase;
    }

    /** The logical side of the event, always {@link Side#CLIENT}. */
    public Side side() {
        return Side.CLIENT;
    }

    /** The local player tick is starting. */
    public static final class Pre extends ClientPlayerTickEvent {

        /**
         * Creates a pre-tick event.
         *
         * @param player the ticking local player
         */
        public Pre(Object player) {
            super(player, Phase.PRE);
        }
    }

    /** The local player tick finished normally. */
    public static final class Post extends ClientPlayerTickEvent {

        /**
         * Creates a post-tick event.
         *
         * @param player the ticking local player
         */
        public Post(Object player) {
            super(player, Phase.POST);
        }
    }
}
