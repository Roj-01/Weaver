package net.weaver.api.event.events;

public class ServerStoppedEvent extends ServerLifecycleEvent
{
    public ServerStoppedEvent(Object server)
    {
        super(server);
    }
}
