package net.weaver.core.command;

public final class FakeCommands
{
    private final Object dispatcher;

    public FakeCommands(Object dispatcher)
    {
        this.dispatcher = dispatcher;
    }

    public Object getDispatcher()
    {
        return dispatcher;
    }
}
