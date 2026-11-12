package net.weaver.api.event.events;

public class ServerStartingEvent extends ServerLifecycleEvent
{
    public ServerStartingEvent(Object server)
    {
        super(server);
    }
}
