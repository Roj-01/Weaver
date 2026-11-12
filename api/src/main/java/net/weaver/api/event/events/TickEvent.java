package net.weaver.api.event.events;

import net.weaver.api.event.Event;
import net.weaver.api.event.Side;

import java.util.Objects;

public abstract class TickEvent extends Event
{
    private final Side side;
    private final long tick;

    protected TickEvent(Side side, long tick)
    {
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
}
