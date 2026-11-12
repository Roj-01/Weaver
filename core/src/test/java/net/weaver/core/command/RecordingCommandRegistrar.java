package net.weaver.core.command;

import net.weaver.api.command.CommandSpec;

import java.util.ArrayList;
import java.util.List;

final class RecordingCommandRegistrar implements CommandRegistrar
{
    private final List<CommandSpec> specs = new ArrayList<>();
    private final List<Object> dispatchers = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final List<Object> removedNodes = new ArrayList<>();
    private boolean available = true;
    private boolean availabilityThrows;
    private String failingName;

    @Override
    public boolean isAvailable()
    {
        if (availabilityThrows)
        {
            throw new IllegalStateException("availability failed");
        }
        return available;
    }

    @Override
    public Object register(Object dispatcher, CommandSpec spec)
    {
        if (spec.name().equals(failingName))
        {
            throw new IllegalStateException("registration failed for " + spec.name());
        }
        specs.add(spec);
        dispatchers.add(dispatcher);
        names.add(spec.name());
        return new Object();
    }

    @Override
    public void removeNode(Object dispatcher, Object node)
    {
        removedNodes.add(node);
    }

    @Override
    public List<String> commandNames(Object dispatcher)
    {
        return List.copyOf(names);
    }

    List<Object> removedNodes()
    {
        return List.copyOf(removedNodes);
    }

    int registerCalls()
    {
        return specs.size();
    }

    List<CommandSpec> specs()
    {
        return List.copyOf(specs);
    }

    List<Object> dispatchers()
    {
        return List.copyOf(dispatchers);
    }

    void setAvailable(boolean available)
    {
        this.available = available;
    }

    void setAvailabilityThrows(boolean availabilityThrows)
    {
        this.availabilityThrows = availabilityThrows;
    }

    void setFailingName(String failingName)
    {
        this.failingName = failingName;
    }
}
