package net.weaver.api.event.events;

import net.weaver.api.event.Event;
import net.weaver.api.event.Side;

import java.util.Objects;

public class ResourceReloadEvent extends Event
{
    public enum Phase
    {
        START,
        FINISHED,
        FAILED
    }

    private final Object source;
    private final Side side;
    private final Phase phase;

    public ResourceReloadEvent(Object source, Side side, Phase phase)
    {
        this.source = Objects.requireNonNull(source, "source");
        this.side = Objects.requireNonNull(side, "side");
        this.phase = Objects.requireNonNull(phase, "phase");
    }

    public Object source()
    {
        return source;
    }

    public Side side()
    {
        return side;
    }

    public Phase phase()
    {
        return phase;
    }
}
