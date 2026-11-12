package net.weaver.core.command;

import net.weaver.api.command.CommandBackend;
import net.weaver.api.command.CommandNodeSpec;
import net.weaver.api.command.CommandRegistration;
import net.weaver.api.command.CommandRegistrationException;
import net.weaver.api.command.CommandRegistry;
import net.weaver.api.command.CommandSpec;
import net.weaver.api.command.PermissionNode;
import net.weaver.api.command.PermissionPolicy;
import net.weaver.api.command.PermissionRegistration;
import net.weaver.api.command.PermissionService;
import net.weaver.api.event.Event;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.EventListener;
import net.weaver.api.event.EventPriority;
import net.weaver.api.event.ListenerHandle;
import net.weaver.api.event.events.CommandRegisterEvent;
import net.weaver.api.event.events.ServerStartedEvent;
import net.weaver.api.event.events.ServerStoppedEvent;
import net.weaver.api.event.events.ServerTickEvent;
import net.weaver.core.LifecycleHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The core command backend that owns the live Brigadier command tree.
 *
 * <p>The backend accepts immutable command specs on any thread, registers their
 * permission nodes, and applies the specs to a dispatcher when the game
 * publishes one through {@link CommandRegisterEvent}. Applying during that
 * callback is safe because the dispatcher is still being constructed. Specs
 * registered after a dispatcher is already known are queued: they are applied
 * by {@link ServerTickEvent.Pre} on the server thread when the game next ticks,
 * never by the registering thread.
 *
 * <p>All state is guarded by one lock. The registrar is the only extension
 * point; production uses a reflective Brigadier registrar and tests inject a
 * recording implementation. Registrar failures are logged and isolated so one
 * bad spec cannot stop the remaining commands. Unregistering a command while a
 * dispatcher is live detaches its root and aliases, re-registers the remaining
 * active specs, records a new snapshot, and resends the tree. Because 26.3
 * keeps the {@code Commands} dispatcher in a final field, the rebuild mutates
 * the existing dispatcher in place.
 *
 * <p>The singleton returned by {@link #instance()} installs itself on first
 * access so lazy resolution through {@link CommandRegistry} receives events.
 * It uses the core class loader; the Brigadier registrar falls back to the
 * dispatcher's loader when necessary.
 */
public final class CoreCommandBackend implements CommandBackend
{
    private static final Object INSTANCE_LOCK = new Object();
    private static volatile CoreCommandBackend instance;

    private final CommandRegistrar registrar;
    private final Object lock = new Object();
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<Object, Set<Entry>> appliedByDispatcher = new IdentityHashMap<>();
    private final Map<Object, Set<Object>> ownedByDispatcher = new IdentityHashMap<>();
    private final List<PermissionRegistration> permissionHandles = new ArrayList<>();
    private final List<ListenerHandle> listenerHandles = new ArrayList<>();
    private final AtomicBoolean resendFailureReported = new AtomicBoolean();
    private volatile Object currentDispatcher;
    private volatile Object currentCommands;
    private volatile Object currentServer;
    private boolean installed;
    private boolean rebuildFailureReported;
    private long nextSequence;

    /**
     * Creates a backend with a registrar.
     *
     * <p>Package-private: production uses {@link #instance()} and tests inject
     * a recording registrar.
     *
     * @param registrar the registrar, not {@code null}
     */
    CoreCommandBackend(CommandRegistrar registrar)
    {
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }

    /**
     * Returns the lazily created process-wide backend.
     *
     * <p>The singleton installs itself before it is returned so that lazy
     * resolution through {@link CommandRegistry} also receives command
     * registration and server tick events. A failed installation is logged and
     * still returns the backend; callers can retry {@link #install()} manually.
     *
     * @return the singleton, never {@code null}
     */
    public static CoreCommandBackend instance()
    {
        CoreCommandBackend local = instance;
        if (local == null)
        {
            synchronized (INSTANCE_LOCK)
            {
                local = instance;
                if (local == null)
                {
                    local = new CoreCommandBackend(
                            new BrigadierRegistrar(CoreCommandBackend.class.getClassLoader()));
                    instance = local;
                }
            }
        }
        try
        {
            local.install();
        }
        catch (Throwable failure)
        {
            logLine("[weaver] COMMAND BACKEND INSTALL FAILED reason=" + failure);
        }
        return local;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAvailable()
    {
        try
        {
            return registrar.isAvailable();
        }
        catch (Throwable failure)
        {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Installs the game permission resolver and registers the command,
     * server-tick, and server-stopped listeners. Repeated calls are no-ops.
     */
    @Override
    public void install()
    {
        synchronized (lock)
        {
            if (installed)
            {
                return;
            }
            installed = true;
            if (!PermissionService.hasStore())
            {
                try
                {
                    PermissionService.install(new MinecraftPermissionResolver());
                }
                catch (Throwable failure)
                {
                    logLine("[weaver] COMMAND PERMISSION RESOLVER FAILED reason=" + failure);
                }
            }
            addListener(CommandRegisterEvent.class,
                    (EventListener<CommandRegisterEvent>) this::onCommandRegister);
            addListener(ServerStartedEvent.class,
                    (EventListener<ServerStartedEvent>) this::onServerStarted);
            addListener(ServerTickEvent.Pre.class,
                    (EventListener<ServerTickEvent.Pre>) this::onServerTick);
            addListener(ServerStoppedEvent.class,
                    (EventListener<ServerStoppedEvent>) this::onServerStopped);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Removes the backend listeners, marks every command registration
     * inactive, clears the dispatcher and applied tracking, and releases the
     * permission registrations this backend created. Global permission state
     * and explicit subject decisions are deliberately left alone because other
     * mods own them. Repeated calls are no-ops.
     */
    @Override
    public void uninstall()
    {
        List<ListenerHandle> listeners;
        List<PermissionRegistration> permissions;
        synchronized (lock)
        {
            installed = false;
            for (Entry entry : entries.values())
            {
                entry.deactivate();
            }
            entries.clear();
            appliedByDispatcher.clear();
            ownedByDispatcher.clear();
            currentDispatcher = null;
            currentCommands = null;
            currentServer = null;
            listeners = new ArrayList<>(listenerHandles);
            listenerHandles.clear();
            permissions = new ArrayList<>(permissionHandles);
            permissionHandles.clear();
        }
        try
        {
            EventBus.GAME.unregister(this);
        }
        catch (Throwable failure)
        {
            logLine("[weaver] COMMAND LISTENER REMOVE FAILED reason=" + failure);
        }
        for (ListenerHandle listener : listeners)
        {
            try
            {
                listener.unregister();
            }
            catch (Throwable ignored)
            {
            }
        }
        for (PermissionRegistration permission : permissions)
        {
            try
            {
                permission.unregister();
            }
            catch (Throwable ignored)
            {
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Duplicate root names and aliases of other active specs are rejected.
     * Permission nodes in the spec tree are registered exactly once per
     * definition. If a dispatcher is already known the new spec is marked
     * pending and is not applied until the next server tick.
     */
    @Override
    public CommandRegistration register(CommandSpec spec)
    {
        Objects.requireNonNull(spec, "spec");
        synchronized (lock)
        {
            rejectDuplicate(spec);
            List<PermissionRegistration> permissions = registerPermissions(spec);
            permissionHandles.addAll(permissions);
            Entry entry = new Entry(this, spec, ++nextSequence);
            entry.pending = currentDispatcher != null;
            entries.put(spec.name(), entry);
            return entry;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object dispatcher()
    {
        return currentDispatcher;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> commandNames()
    {
        synchronized (lock)
        {
            return namesLocked();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses the game {@code Commands#sendCommands(ServerPlayer)} path when
     * the event handler captured the command holder. When a player list is
     * {@code null}, the online players reported by the current server are
     * used. Failures are logged once and never thrown.
     */
    @Override
    public void resendTree(Object dispatcher, List<Object> players, List<String> names)
    {
        if (dispatcher == null)
        {
            return;
        }
        List<Object> targets = players;
        if (targets == null)
        {
            Object server = currentServer;
            try
            {
                targets = server == null ? List.of() : GameReflection.forLoader(
                        server.getClass().getClassLoader()).players(server);
            }
            catch (Throwable failure)
            {
                targets = List.of();
            }
        }
        if (targets.isEmpty())
        {
            return;
        }
        try
        {
            CommandPackets.resend(currentCommands, dispatcher, targets);
        }
        catch (Throwable failure)
        {
            if (resendFailureReported.compareAndSet(false, true))
            {
                logLine("[weaver] COMMAND TREE RESEND FAILED reason=" + failure);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Rebuilds the Weaver portion of the live dispatcher in place: every
     * root literal and alias owned by Weaver is detached, then each active
     * spec is registered again through the same {@link CommandRegistrar}. The
     * game's {@code Commands} holder keeps a final dispatcher field, so the
     * rebuild mutates the existing dispatcher rather than replacing it. After
     * a successful rebuild the tree is recorded with the {@code rebuild}
     * source label and resent to online players.
     */
    @Override
    public void rebuildTree(Object dispatcher, List<Object> players)
    {
        if (dispatcher == null)
        {
            return;
        }
        List<String> names;
        synchronized (lock)
        {
            try
            {
                Set<Object> owned = ownedLocked(dispatcher);
                for (Object node : new ArrayList<>(owned))
                {
                    try
                    {
                        registrar.removeNode(dispatcher, node);
                    }
                    catch (Throwable failure)
                    {
                        logRebuildFailure(failure);
                    }
                }
                owned.clear();
                Set<Entry> applied = appliedLocked(dispatcher);
                applied.clear();
                for (Entry entry : entries.values())
                {
                    entry.pending = false;
                    if (!entry.isActive())
                    {
                        continue;
                    }
                    applied.add(entry);
                    applySafely(dispatcher, entry);
                }
                names = namesLocked();
            }
            catch (Throwable failure)
            {
                logRebuildFailure(failure);
                return;
            }
        }
        recordTreeSync(dispatcher, names, "rebuild");
        resendTree(dispatcher, players, names);
    }

    /**
     * Applies pending specs to the current dispatcher on the server thread.
     *
     * <p>Called only from the {@link ServerTickEvent.Pre} listener. A tick with
     * no pending spec does nothing, so repeated ticks never duplicate
     * registrations. After a successful flush the tree is recorded with the
     * {@code server-tick} source label and resent to online players.
     */
    void flushPending()
    {
        Object dispatcher;
        List<String> names;
        synchronized (lock)
        {
            dispatcher = currentDispatcher;
            if (dispatcher == null || !hasPendingLocked())
            {
                return;
            }
            Set<Entry> applied = appliedLocked(dispatcher);
            for (Entry entry : new ArrayList<>(entries.values()))
            {
                if (!entry.isActive() || !entry.pending)
                {
                    continue;
                }
                applied.add(entry);
                entry.pending = false;
                applySafely(dispatcher, entry);
            }
            names = namesLocked();
        }
        recordTreeSync(dispatcher, names, "server-tick");
        resendTree(dispatcher, null, names);
    }

    private void onCommandRegister(CommandRegisterEvent event)
    {
        if (event == null)
        {
            return;
        }
        Object dispatcher;
        try
        {
            dispatcher = event.dispatcher();
        }
        catch (Throwable failure)
        {
            logLine("[weaver] COMMAND REGISTER EVENT FAILED reason=" + failure);
            return;
        }
        if (dispatcher == null)
        {
            logLine("[weaver] COMMAND REGISTER SKIPPED reason=null-dispatcher");
            return;
        }
        Object commands;
        try
        {
            commands = event.commands();
        }
        catch (Throwable failure)
        {
            commands = null;
        }
        List<String> names;
        synchronized (lock)
        {
            currentDispatcher = dispatcher;
            currentCommands = commands;
            Set<Entry> applied = appliedLocked(dispatcher);
            for (Entry entry : new ArrayList<>(entries.values()))
            {
                if (!entry.isActive())
                {
                    continue;
                }
                if (applied.contains(entry))
                {
                    entry.pending = false;
                    continue;
                }
                applied.add(entry);
                entry.pending = false;
                applySafely(dispatcher, entry);
            }
            names = namesLocked();
        }
        recordTreeSync(dispatcher, names, "server");
    }

    private void onServerStarted(ServerStartedEvent event)
    {
        try
        {
            currentServer = event.server();
        }
        catch (Throwable failure)
        {
            logLine("[weaver] COMMAND SERVER CAPTURE FAILED reason=" + failure);
        }
    }

    private void onServerTick(ServerTickEvent.Pre event)
    {
        try
        {
            flushPending();
        }
        catch (Throwable failure)
        {
            logLine("[weaver] COMMAND TICK FLUSH FAILED reason=" + failure);
        }
    }

    private void onServerStopped(ServerStoppedEvent event)
    {
        synchronized (lock)
        {
            currentDispatcher = null;
            currentCommands = null;
            currentServer = null;
            appliedByDispatcher.clear();
            ownedByDispatcher.clear();
            for (Entry entry : entries.values())
            {
                if (entry.isActive())
                {
                    entry.pending = true;
                }
            }
        }
    }

    private void addListener(Class<? extends Event> eventType,
            EventListener<? extends Event> listener)
    {
        try
        {
            @SuppressWarnings("unchecked")
            EventListener<Event> cast = (EventListener<Event>) listener;
            listenerHandles.add(EventBus.GAME.addListener(eventType, this, cast,
                    EventPriority.NORMAL, false));
        }
        catch (Throwable failure)
        {
            logLine("[weaver] COMMAND LISTENER ADD FAILED event=" + eventType.getName()
                    + " reason=" + failure);
        }
    }

    private void applySafely(Object dispatcher, Entry entry)
    {
        try
        {
            List<Object> nodes = registrar.registerTree(dispatcher, entry.spec);
            ownedLocked(dispatcher).addAll(nodes);
        }
        catch (Throwable failure)
        {
            logLine("[weaver] COMMAND REGISTER FAILED name=" + entry.spec.name()
                    + " reason=" + failure);
        }
    }

    private void onRegistrationUnregistered(Entry entry)
    {
        Object dispatcher = currentDispatcher;
        if (dispatcher == null)
        {
            return;
        }
        rebuildTree(dispatcher, null);
    }

    private void logRebuildFailure(Throwable failure)
    {
        synchronized (lock)
        {
            if (rebuildFailureReported)
            {
                return;
            }
            rebuildFailureReported = true;
        }
        logLine("[weaver] COMMAND TREE REBUILD FAILED reason=" + failure);
    }

    private void rejectDuplicate(CommandSpec spec)
    {
        for (Entry entry : entries.values())
        {
            if (!entry.isActive())
            {
                continue;
            }
            CommandSpec existing = entry.spec;
            if (existing.name().equals(spec.name()))
            {
                throw new CommandRegistrationException(
                        "Command name already registered: " + spec.name());
            }
            if (existing.aliases().contains(spec.name()))
            {
                throw new CommandRegistrationException(
                        "Command name conflicts with an alias: " + spec.name());
            }
            for (String alias : spec.aliases())
            {
                if (existing.name().equals(alias))
                {
                    throw new CommandRegistrationException(
                            "Command alias conflicts with a registered command: " + alias);
                }
                if (existing.aliases().contains(alias))
                {
                    throw new CommandRegistrationException(
                            "Command alias already registered: " + alias);
                }
            }
        }
    }

    private List<PermissionRegistration> registerPermissions(CommandSpec spec)
    {
        List<PermissionRegistration> handles = new ArrayList<>();
        Map<PermissionNode, PermissionConfig> seen = new HashMap<>();
        try
        {
            registerPermission(spec.permission(), spec.permissionPolicy(), spec.opLevel(), seen,
                    handles);
            for (CommandNodeSpec child : spec.children())
            {
                registerNodePermissions(child, seen, handles);
            }
            return handles;
        }
        catch (Throwable failure)
        {
            for (PermissionRegistration handle : handles)
            {
                try
                {
                    handle.unregister();
                }
                catch (Throwable ignored)
                {
                }
            }
            if (failure instanceof CommandRegistrationException registrationException)
            {
                throw registrationException;
            }
            throw new CommandRegistrationException(
                    "Failed to register permissions for command " + spec.name(), failure);
        }
    }

    private void registerNodePermissions(CommandNodeSpec node,
            Map<PermissionNode, PermissionConfig> seen, List<PermissionRegistration> handles)
    {
        registerPermission(node.permission(), node.permissionPolicy(), node.opLevel(), seen,
                handles);
        for (CommandNodeSpec child : node.children())
        {
            registerNodePermissions(child, seen, handles);
        }
    }

    private void registerPermission(PermissionNode node, PermissionPolicy policy, int opLevel,
            Map<PermissionNode, PermissionConfig> seen, List<PermissionRegistration> handles)
    {
        if (node == null)
        {
            return;
        }
        PermissionConfig existing = seen.get(node);
        if (existing != null)
        {
            if (existing.policy != policy || existing.opLevel != opLevel)
            {
                throw new CommandRegistrationException("Permission node " + node
                        + " is used with conflicting policy or op level in one command");
            }
            return;
        }
        handles.add(PermissionService.register(node, policy, opLevel));
        seen.put(node, new PermissionConfig(policy, opLevel));
    }

    private Set<Entry> appliedLocked(Object dispatcher)
    {
        return appliedByDispatcher.computeIfAbsent(dispatcher,
                key -> Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private Set<Object> ownedLocked(Object dispatcher)
    {
        return ownedByDispatcher.computeIfAbsent(dispatcher,
                key -> Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private boolean hasPendingLocked()
    {
        for (Entry entry : entries.values())
        {
            if (entry.isActive() && entry.pending)
            {
                return true;
            }
        }
        return false;
    }

    private List<String> namesLocked()
    {
        List<String> names = new ArrayList<>();
        for (Entry entry : entries.values())
        {
            if (!entry.isActive())
            {
                continue;
            }
            names.add(entry.spec.name());
            names.addAll(entry.spec.aliases());
        }
        return List.copyOf(names);
    }

    private static void recordTreeSync(Object dispatcher, List<String> names, String source)
    {
        try
        {
            CommandRegistry.recordTreeSync(dispatcher, names, source);
        }
        catch (Throwable failure)
        {
            logLine("[weaver] COMMAND TREE SYNC FAILED source=" + source + " reason=" + failure);
        }
    }

    private static void logLine(String message)
    {
        try
        {
            LifecycleHooks.logLine(message);
        }
        catch (Throwable ignored)
        {
        }
    }

    private static final class Entry implements CommandRegistration
    {
        private final CoreCommandBackend backend;
        private final CommandSpec spec;
        private final long sequence;
        private volatile boolean active = true;
        private volatile boolean pending;

        private Entry(CoreCommandBackend backend, CommandSpec spec, long sequence)
        {
            this.backend = backend;
            this.spec = spec;
            this.sequence = sequence;
        }

        @Override
        public CommandSpec spec()
        {
            return spec;
        }

        @Override
        public boolean isActive()
        {
            return active;
        }

        @Override
        public void unregister()
        {
            synchronized (this)
            {
                if (!active)
                {
                    return;
                }
                active = false;
            }
            backend.onRegistrationUnregistered(this);
        }

        private void deactivate()
        {
            active = false;
        }
    }

    private record PermissionConfig(PermissionPolicy policy, int opLevel)
    {
    }
}
