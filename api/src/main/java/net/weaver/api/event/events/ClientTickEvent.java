package net.weaver.api.event.events;

import net.weaver.api.event.Side;

public abstract class ClientTickEvent extends TickEvent
{
    protected ClientTickEvent(long tick)
    {
        super(Side.CLIENT, tick);
    }

    public static final class Pre extends ClientTickEvent
    {
        public Pre(long tick)
        {
            super(tick);
        }
    }

    public static final class Post extends ClientTickEvent
    {
        public Post(long tick)
        {
            super(tick);
        }
    }
}
