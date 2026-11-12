package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

public class CommandRegisterEvent extends Event
{
    private static volatile Method dispatcherMethod;

    private final Object commands;

    public CommandRegisterEvent(Object commands)
    {
        this.commands = Objects.requireNonNull(commands, "commands");
    }

    public Object commands()
    {
        return commands;
    }

    public Object dispatcher()
    {
        final Method method = resolveDispatcherMethod(commands.getClass());
        try
        {
            return method.invoke(commands);
        }
        catch (IllegalAccessException | InvocationTargetException e)
        {
            throw new IllegalStateException(
                    "Failed to call getDispatcher() on " + commands.getClass().getName(), e);
        }
    }

    private static Method resolveDispatcherMethod(Class<?> type)
    {
        final Method cached = dispatcherMethod;
        if (cached != null && cached.getDeclaringClass().isAssignableFrom(type))
        {
            return cached;
        }
        try
        {
            final Method resolved = type.getMethod("getDispatcher");
            dispatcherMethod = resolved;
            return resolved;
        }
        catch (NoSuchMethodException e)
        {
            throw new IllegalStateException(
                    "Class " + type.getName() + " has no public getDispatcher() method", e);
        }
    }
}
