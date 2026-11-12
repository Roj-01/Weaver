package net.weaver.api.event.events;

import net.weaver.api.ModContext;
import net.weaver.api.event.Event;
import net.weaver.api.event.ModScopedEvent;

import java.util.Objects;

public final class ModLifecycleEvent extends Event implements ModScopedEvent
{
    public enum Phase
    {
        INITIALIZE,
        REGISTER,
        COMMON_SETUP,
        CLIENT_SETUP,
        SERVER_SETUP,
        LOAD_COMPLETE
    }

    private final ModContext context;
    private final Phase phase;

    public ModLifecycleEvent(ModContext context, Phase phase)
    {
        this.context = Objects.requireNonNull(context, "context");
        this.phase = Objects.requireNonNull(phase, "phase");
    }

    public ModContext context()
    {
        return context;
    }

    public String modId()
    {
        return context.id();
    }

    public Phase phase()
    {
        return phase;
    }
}
