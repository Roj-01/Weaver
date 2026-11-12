package net.weaver.core.command;

import net.weaver.core.LifecycleHooks;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Best-effort command-tree resend for online players.
 *
 * <p>The core module compiles without the game, so this helper uses reflection
 * only. It prefers the game's own {@code Commands#sendCommands(ServerPlayer)}
 * path, which builds and sends the packet from the live dispatcher. When that
 * path is unavailable it builds a {@code ClientboundCommandsPacket} from the
 * dispatcher root and the game's command node inspector, then sends it through
 * the player connection.
 *
 * <p>Every failure is isolated. The first failure prints one
 * {@code [weaver] COMMAND TREE RESEND FAILED} line; later failures are silent.
 * Nothing in this class throws to its caller.
 */
final class CommandPackets
{
    private static final AtomicBoolean FAILURE_REPORTED = new AtomicBoolean();

    private CommandPackets()
    {
    }

    /**
     * Resends the command tree to the supplied players.
     *
     * @param commands   the raw game {@code Commands} holder, or {@code null}
     * @param dispatcher the raw dispatcher, or {@code null}
     * @param players    the target players, never {@code null}
     */
    static void resend(Object commands, Object dispatcher, List<Object> players)
    {
        if (dispatcher == null || players == null || players.isEmpty())
        {
            return;
        }
        if (commands != null && sendWithCommands(commands, players))
        {
            return;
        }
        buildAndSend(dispatcher, players);
    }

    private static boolean sendWithCommands(Object commands, List<Object> players)
    {
        Method send = singleArgumentMethod(commands.getClass(), "sendCommands");
        if (send == null)
        {
            return false;
        }
        for (Object player : players)
        {
            if (player == null)
            {
                return false;
            }
            try
            {
                send.invoke(commands, player);
            }
            catch (Throwable failure)
            {
                return false;
            }
        }
        return true;
    }

    private static void buildAndSend(Object dispatcher, List<Object> players)
    {
        try
        {
            ClassLoader loader = dispatcher.getClass().getClassLoader();
            Class<?> packetClass = load(
                    "net.minecraft.network.protocol.game.ClientboundCommandsPacket", loader);
            Class<?> inspectorClass = load(packetClass.getName() + "$NodeInspector", loader);
            Class<?> rootClass = load("com.mojang.brigadier.tree.RootCommandNode", loader);
            Class<?> commandsClass = load("net.minecraft.commands.Commands", loader);
            Class<?> packetBase = load("net.minecraft.network.protocol.Packet", loader);

            Field inspectorField = commandsClass.getDeclaredField("COMMAND_NODE_INSPECTOR");
            inspectorField.setAccessible(true);
            Object inspector = inspectorField.get(null);
            Object root = dispatcher.getClass().getMethod("getRoot").invoke(dispatcher);
            if (inspector == null || root == null)
            {
                report("inspector-unavailable", null);
                return;
            }
            Constructor<?> constructor = packetClass.getConstructor(rootClass, inspectorClass);
            Object packet = constructor.newInstance(root, inspector);
            for (Object player : players)
            {
                if (player == null)
                {
                    continue;
                }
                Field connectionField = player.getClass().getField("connection");
                Object connection = connectionField.get(player);
                if (connection == null)
                {
                    report("connection-unavailable", null);
                    return;
                }
                connection.getClass().getMethod("send", packetBase).invoke(connection, packet);
            }
        }
        catch (Throwable failure)
        {
            report("build", failure);
        }
    }

    private static Method singleArgumentMethod(Class<?> type, String name)
    {
        for (Method method : type.getMethods())
        {
            if (method.getName().equals(name) && method.getParameterCount() == 1)
            {
                return method;
            }
        }
        return null;
    }

    private static Class<?> load(String name, ClassLoader loader) throws ClassNotFoundException
    {
        return Class.forName(name, false, loader);
    }

    private static void report(String stage, Throwable failure)
    {
        if (!FAILURE_REPORTED.compareAndSet(false, true))
        {
            return;
        }
        String reason = failure == null ? stage : stage + ": " + failure;
        try
        {
            LifecycleHooks.logLine("[weaver] COMMAND TREE RESEND FAILED reason=" + reason);
        }
        catch (Throwable ignored)
        {
        }
    }
}
