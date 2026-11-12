package net.weaver.api.event.events;

public class ServerStoppingEvent extends ServerLifecycleEvent
{
    public ServerStoppingEvent(Object server)
    {
        super(server);
    }
}
