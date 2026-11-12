package net.weaver.api.event.events;

import net.weaver.api.event.Side;

import java.util.Objects;

public abstract class LevelTickEvent extends LevelEvent
{
    private final Side side;
    private final long tick;

    public LevelTickEvent(Object level, Side side, long tick)
    {
        super(level);
        this.side = Objects.requireNonNull(side, "side");
        this.tick = tick;
    }

    public Side side()
    {
        return side;
    }

    public long tick()
    {
        return tick;
    }

    public static final class Pre extends LevelTickEvent
    {
        public Pre(Object level, Side side, long tick)
        {
            super(level, side, tick);
        }
    }

    public static final class Post extends LevelTickEvent
    {
        public Post(Object level, Side side, long tick)
        {
            super(level, side, tick);
        }
    }
}
