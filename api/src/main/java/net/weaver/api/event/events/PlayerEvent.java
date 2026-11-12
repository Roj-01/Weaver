package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

public class PlayerEvent extends Event
{
    private final Object playerList;
    private final Object player;

    public PlayerEvent(Object playerList, Object player)
    {
        this.playerList = Objects.requireNonNull(playerList, "playerList");
        this.player = Objects.requireNonNull(player, "player");
    }

    public Object playerList()
    {
        return playerList;
    }

    public Object player()
    {
        return player;
    }
}
