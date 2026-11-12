package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

public class LevelEvent extends Event
{
    private final Object level;

    public LevelEvent(Object level)
    {
        this.level = Objects.requireNonNull(level, "level");
    }

    public Object level()
    {
        return level;
    }
}
