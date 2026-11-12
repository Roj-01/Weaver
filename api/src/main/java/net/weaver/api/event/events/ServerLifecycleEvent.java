package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

public abstract class ServerLifecycleEvent extends Event
{
    private final Object server;

    protected ServerLifecycleEvent(Object server)
    {
        this.server = Objects.requireNonNull(server, "server");
    }

    public Object server()
    {
        return server;
    }
}
