package net.weaver.api.event.events;

public class ScreenOpenEvent extends ScreenEvent
{
    public ScreenOpenEvent(Object minecraft, Object screen)
    {
        super(minecraft, screen, true);
    }
}
