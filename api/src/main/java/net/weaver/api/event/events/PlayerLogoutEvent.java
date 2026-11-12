package net.weaver.api.event.events;

public class PlayerLogoutEvent extends PlayerEvent
{
    public PlayerLogoutEvent(Object playerList, Object player)
    {
        super(playerList, player);
    }
}
