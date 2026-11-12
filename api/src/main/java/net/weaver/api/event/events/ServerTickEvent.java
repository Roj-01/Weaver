package net.weaver.api.event.events;

import net.weaver.api.event.Side;

public abstract class ServerTickEvent extends TickEvent
{
    protected ServerTickEvent(long tick)
    {
        super(Side.SERVER, tick);
    }

    public static final class Pre extends ServerTickEvent
    {
        public Pre(long tick)
        {
            super(tick);
        }
    }

    public static final class Post extends ServerTickEvent
    {
        public Post(long tick)
        {
            super(tick);
        }
    }
}
