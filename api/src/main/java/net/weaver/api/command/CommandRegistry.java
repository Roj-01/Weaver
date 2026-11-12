package net.weaver.api.command;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.events.command.CommandTreeSyncEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-wide, thread-safe facade for command registration and tree state.
 *
 * <p>The facade is game-free. It resolves a {@link CommandBackend} lazily: if
 * no backend has been installed, it first checks for Brigadier with
 * {@code com.mojang.brigadier.CommandDispatcher}. When Brigadier is absent,
 * resolution fails without ever loading a core command class. When Brigadier
 * is present, it loads {@code net.weaver.core.command.CoreCommandBackend},
 * calls its public static no-arg {@code instance()} factory, casts the result,
 * and caches it. A backend may also be installed explicitly by the loader or a
 * test through {@link #install(CommandBackend)}.
 *
 * <p>Commands are server-side authority. Mods should register during
 * {@code onInitialize} or {@code onRegister} so the specs exist before the
 * server builds its dispatcher. Late registrations are accepted by the facade
 * and are queued by the core backend, which applies them on the server thread
 * when it can. Aliases are additional root literals, redirects point at sibling
 * literals in the same parent, and suggestions are evaluated by the backend
 * while the game builds a suggestion list.
 *
 * <p>Client command-tree synchronization is implemented by the built-in
 * {@code ClientCommandTreeMixin} in {@code api-game}. After a dispatcher has
 * been populated, the core backend calls
 * {@link #recordTreeSync(Object, List, String)} with source {@code "server"},
 * {@code "server-tick"}, or {@code "rebuild"}. The client mixin calls it after
 * the game applies a server command packet with source {@code "client"}. The
 * method stores an immutable {@link CommandTreeSnapshot} and posts a
 * {@link CommandTreeSyncEvent} on {@link EventBus#GAME}.
 *
 * <p>All methods are thread-safe. Backend resolution is cached and guarded by
 * a lock; snapshots use a separate lock and a monotonic sequence. Read methods
 * never retain caller-owned lists.
 */
public final class CommandRegistry
{
    private static final String BRIGADIER_DISPATCHER_CLASS =
            "com.mojang.brigadier.CommandDispatcher";
    private static final String CORE_BACKEND_CLASS =
            "net.weaver.core.command.CoreCommandBackend";
    private static final Object LOCK = new Object();
    private static final Object SNAPSHOT_LOCK = new Object();
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private static volatile CommandBackend backend;
    private static volatile Boolean brigadierPresent;
    private static volatile CommandTreeSnapshot treeSnapshot;

    private CommandRegistry()
    {
    }

    /**
     * Installs a backend explicitly.
     *
     * <p>Intended for the loader and for tests. The backend's {@link
     * CommandBackend#install()} method is called before the backend becomes
     * visible to other callers. If installation throws, the previously visible
     * backend is left in place.
     *
     * @param newBackend the backend, not {@code null}
     * @throws NullPointerException when {@code newBackend} is {@code null}
     * @throws CommandException     when the backend's installation fails
     */
    public static void install(CommandBackend newBackend)
    {
        Objects.requireNonNull(newBackend, "backend");
        newBackend.install();
        synchronized (LOCK)
        {
            backend = newBackend;
        }
    }

    /**
     * Uninstalls the active backend and clears the cached tree snapshot.
     *
     * <p>This method acts only on a backend that was installed or previously
     * resolved; it never triggers lazy resolution. When no backend is active,
     * it is a no-op. Backend uninstall failures are reported to standard error
     * and ignored so shutdown can continue. The backend reference and the last
     * tree snapshot are cleared even when the backend's own uninstall fails.
     * This method is thread-safe.
     */
    public static void uninstall()
    {
        CommandBackend current;
        synchronized (LOCK)
        {
            current = backend;
            backend = null;
            treeSnapshot = null;
        }
        uninstallQuietly(current);
    }

    /**
     * Clears all cached facade state and uninstalls the active backend.
     *
     * <p>This is a test and embedding helper. Like {@link #uninstall()}, it
     * never triggers lazy resolution. It additionally forgets the cached
     * Brigadier presence probe so a later call re-probes the class path. It
     * does not reset permission state or rewind the snapshot sequence. This
     * method is thread-safe.
     */
    public static void reset()
    {
        CommandBackend current;
        synchronized (LOCK)
        {
            current = backend;
            backend = null;
            brigadierPresent = null;
            treeSnapshot = null;
        }
        uninstallQuietly(current);
    }

    /**
     * Whether a usable command backend is currently available.
     *
     * <p>This method never throws and never triggers lazy resolution or loads a
     * core command class. It reports only a backend that was installed through
     * {@link #install(CommandBackend)} or resolved by an earlier call to
     * {@link #backend()}, {@link #register(CommandSpec)}, {@link #dispatcher()},
     * or {@link #commandNames()}. Backend availability failures are reported as
     * {@code false}.
     *
     * @return {@code true} when a backend is present and reports itself
     *         available
     */
    public static boolean isAvailable()
    {
        CommandBackend current = backend;
        if (current == null)
        {
            return false;
        }
        try
        {
            return current.isAvailable();
        }
        catch (Throwable failure)
        {
            return false;
        }
    }

    /**
     * Returns the active backend, resolving it lazily.
     *
     * @return the backend, never {@code null}
     * @throws CommandUnavailableException when Brigadier or the core backend is
     *                                     absent or cannot be instantiated
     */
    public static CommandBackend backend()
    {
        CommandBackend current = resolveBackend(true);
        if (current == null)
        {
            throw new CommandUnavailableException("Weaver command backend is unavailable");
        }
        return current;
    }

    /**
     * Registers a command specification.
     *
     * <p>Mods should call this during {@code onInitialize} or {@code onRegister}
     * so the spec exists before the server builds the dispatcher. Late
     * registrations are queued by the core backend and applied on the server
     * thread when possible. This method is thread-safe.
     *
     * @param spec the specification, not {@code null}
     * @return the registration handle, never {@code null}
     * @throws NullPointerException        when {@code spec} is {@code null}
     * @throws CommandUnavailableException when no backend is available
     * @throws CommandRegistrationException when the backend rejects the spec
     */
    public static CommandRegistration register(CommandSpec spec)
    {
        Objects.requireNonNull(spec, "spec");
        return backend().register(spec);
    }

    /**
     * Returns the raw game dispatcher.
     *
     * @return the raw dispatcher, or {@code null} when the backend has not
     *         built one
     * @throws CommandUnavailableException when no backend is available
     */
    public static Object dispatcher()
    {
        return backend().dispatcher();
    }

    /**
     * Returns the command names known to the active backend.
     *
     * @return an immutable name list, never {@code null}
     * @throws CommandUnavailableException when no backend is available
     */
    public static List<String> commandNames()
    {
        return immutableNames(backend().commandNames());
    }

    /**
     * Returns the last recorded command tree snapshot.
     *
     * @return the last snapshot, or {@code null} when none has been recorded
     *         since the last reset
     */
    public static CommandTreeSnapshot treeSnapshot()
    {
        return treeSnapshot;
    }

    /**
     * Records a command tree synchronization point.
     *
     * <p>Called by the core backend after a dispatcher is populated, and by the
     * built-in client command-tree mixin after the game applies a server
     * command packet. The method stores an immutable snapshot with a
     * monotonically increasing sequence, then posts a
     * {@link CommandTreeSyncEvent} on {@link EventBus#GAME}. A {@code null}
     * dispatcher is allowed and is stored as {@code null}. Event listener
     * failures cannot break the caller: any throwable from posting is ignored.
     * This method is thread-safe; concurrent calls are serialized so the stored
     * snapshot always has the highest sequence assigned so far.
     *
     * @param dispatcher   the raw dispatcher, possibly {@code null}
     * @param commandNames the command names, or {@code null} for none
     * @param source       the source label, for example {@code "server"}
     */
    public static void recordTreeSync(Object dispatcher, List<String> commandNames, String source)
    {
        List<String> names = immutableNames(commandNames);
        synchronized (SNAPSHOT_LOCK)
        {
            long sequence = SEQUENCE.incrementAndGet();
            treeSnapshot = new CommandTreeSnapshot(dispatcher, names, source, sequence);
        }
        try
        {
            EventBus.GAME.post(new CommandTreeSyncEvent(dispatcher, names, source));
        }
        catch (Throwable ignored)
        {
        }
    }

    /**
     * Clears the last recorded command tree snapshot.
     *
     * <p>The global sequence counter is not rewound, so subsequent snapshots
     * remain monotonically increasing across resets. This method is
     * thread-safe.
     */
    public static void resetTreeSnapshot()
    {
        synchronized (SNAPSHOT_LOCK)
        {
            treeSnapshot = null;
        }
    }

    private static CommandBackend resolveBackend(boolean required)
    {
        CommandBackend current = backend;
        if (current != null)
        {
            return current;
        }
        synchronized (LOCK)
        {
            current = backend;
            if (current != null)
            {
                return current;
            }
            if (!brigadierPresent())
            {
                if (required)
                {
                    throw new CommandUnavailableException(
                            "Brigadier is not present; the Weaver command API is unavailable");
                }
                return null;
            }
            try
            {
                ClassLoader loader = CommandRegistry.class.getClassLoader();
                Class<?> coreType = Class.forName(CORE_BACKEND_CLASS, true, loader);
                Method instanceMethod = coreType.getMethod("instance");
                if (!Modifier.isStatic(instanceMethod.getModifiers())
                        || instanceMethod.getParameterCount() != 0)
                {
                    throw new IllegalStateException(CORE_BACKEND_CLASS
                            + " does not declare a public static no-arg instance() method");
                }
                Object value = instanceMethod.invoke(null);
                if (!(value instanceof CommandBackend resolved))
                {
                    throw new IllegalStateException(CORE_BACKEND_CLASS
                            + ".instance() did not return a CommandBackend");
                }
                backend = resolved;
                return resolved;
            }
            catch (InvocationTargetException failure)
            {
                Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
                if (required)
                {
                    throw new CommandUnavailableException(
                            "Failed to initialize the Weaver command backend", cause);
                }
                return null;
            }
            catch (Throwable failure)
            {
                if (required)
                {
                    throw new CommandUnavailableException(
                            "The Weaver command backend is unavailable", failure);
                }
                return null;
            }
        }
    }

    private static boolean brigadierPresent()
    {
        Boolean present = brigadierPresent;
        if (present != null)
        {
            return present;
        }
        boolean detected;
        try
        {
            Class.forName(BRIGADIER_DISPATCHER_CLASS, false, CommandRegistry.class.getClassLoader());
            detected = true;
        }
        catch (Throwable failure)
        {
            detected = false;
        }
        brigadierPresent = detected;
        return detected;
    }

    private static void uninstallQuietly(CommandBackend current)
    {
        if (current == null)
        {
            return;
        }
        try
        {
            current.uninstall();
        }
        catch (Throwable failure)
        {
            System.err.println("[weaver] COMMAND BACKEND UNINSTALL FAILED backend="
                    + current.getClass().getName() + " reason=" + failure);
        }
    }

    private static List<String> immutableNames(List<String> names)
    {
        if (names == null || names.isEmpty())
        {
            return List.of();
        }
        List<String> copy = new ArrayList<>(names.size());
        for (String name : names)
        {
            if (name != null)
            {
                copy.add(name);
            }
        }
        return List.copyOf(copy);
    }
}
