package net.weaver.api.event.events;

public class ScreenCloseEvent extends ScreenEvent
{
    public ScreenCloseEvent(Object minecraft, Object screen)
    {
        super(minecraft, screen, false);
    }
}
