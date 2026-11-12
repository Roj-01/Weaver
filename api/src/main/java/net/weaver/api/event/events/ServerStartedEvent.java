package net.weaver.api.event.events;

public class ServerStartedEvent extends ServerLifecycleEvent
{
    public ServerStartedEvent(Object server)
    {
        super(server);
    }
}
