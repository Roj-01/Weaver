package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

public class ScreenEvent extends Event
{
    private final Object minecraft;
    private final Object screen;
    private final boolean opening;

    public ScreenEvent(Object minecraft, Object screen, boolean opening)
    {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.screen = screen;
        this.opening = opening;
    }

    public Object minecraft()
    {
        return minecraft;
    }

    public Object screen()
    {
        return screen;
    }

    public boolean opening()
    {
        return opening;
    }
}
