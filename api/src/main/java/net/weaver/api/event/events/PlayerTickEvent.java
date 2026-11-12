package net.weaver.api.event.events;

import net.weaver.api.event.Event;
import net.weaver.api.event.Side;

import java.util.Objects;

/** A server-side player tick. */
public abstract class PlayerTickEvent extends Event {

    private final Object player;
    private final long tick;

    protected PlayerTickEvent(Object player, long tick) {
        this.player = Objects.requireNonNull(player, "player");
        this.tick = tick;
    }

    public Object player() {
        return player;
    }

    public long tick() {
        return tick;
    }

    public Side side() {
        return Side.SERVER;
    }

    public static final class Pre extends PlayerTickEvent {

        public Pre(Object player, long tick) {
            super(player, tick);
        }
    }

    public static final class Post extends PlayerTickEvent {

        public Post(Object player, long tick) {
            super(player, tick);
        }
    }
}
