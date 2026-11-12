package net.weaver.core.client;

import net.weaver.api.client.ClientBackend;
import net.weaver.api.client.ClientBlockContext;
import net.weaver.api.client.ClientChunkContext;
import net.weaver.api.client.ClientChunkListener;
import net.weaver.api.client.ClientException;
import net.weaver.api.client.ClientRegistration;
import net.weaver.api.client.ClientTickContext;
import net.weaver.api.client.ClientTickListener;
import net.weaver.api.client.HudLayer;
import net.weaver.api.client.HudRenderContext;
import net.weaver.api.client.KeyMappingSpec;
import net.weaver.api.client.KeyModifiers;
import net.weaver.api.client.ResourceReloadContext;
import net.weaver.api.client.ResourceReloadListener;
import net.weaver.api.client.ScreenFactory;
import net.weaver.api.client.TooltipContext;
import net.weaver.api.client.TooltipProvider;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.EventListener;
import net.weaver.api.event.EventPriority;
import net.weaver.api.event.ListenerHandle;
import net.weaver.api.event.Side;
import net.weaver.api.event.events.ClientTickEvent;
import net.weaver.api.event.events.ResourceReloadEvent;
import net.weaver.core.LifecycleHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core implementation of the game-free client extension backend.
 *
 * <p>The backend bridges {@link ClientBackend} calls to a
 * {@link ClientGameAdapter}. Production code uses {@link #instance()}, which
 * installs a reflection-only adapter that never compiles against game types.
 * Tests use the package-private constructor with a fake adapter.
 *
 * <p>Registration, screen, HUD, and tooltip operations delegate their thread
 * check to the adapter. When the adapter cannot see a client, those operations
 * throw {@link net.weaver.api.client.ClientThreadException} through the
 * adapter's thread assertion. Listeners are isolated so one failing mod never
 * breaks the tick, render, reload, or tooltip loop.
 */
public final class CoreClientBackend implements ClientBackend
{
    private static volatile CoreClientBackend sharedInstance;
    private static volatile CoreClientBackend installedBackend;

    private final ClientGameAdapter adapter;
    private final boolean eventListenersEnabled;
    private final Object lock = new Object();
    private final Set<Registration> registrations =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<KeyEntry> keyMappings = new ArrayList<>();
    private final Map<String, ScreenEntry> screenFactories = new LinkedHashMap<>();
    private final List<HudEntry> hudLayers = new ArrayList<>();
    private final List<TooltipEntry> tooltipProviders = new ArrayList<>();
    private final List<ChunkEntry> chunkListeners = new ArrayList<>();
    private final List<TickEntry> preTickListeners = new ArrayList<>();
    private final List<TickEntry> postTickListeners = new ArrayList<>();
    private final List<ReloadEntry> reloadListeners = new ArrayList<>();

    private ListenerHandle preTickHandle;
    private ListenerHandle postTickHandle;
    private ListenerHandle reloadHandle;
    private boolean installed;
    private long sequence;

    /**
     * Creates a backend for tests or an explicit adapter.
     *
     * @param adapter             the game adapter, never {@code null}
     * @param installEventListeners whether {@link #install()} registers the
     *                              global tick and resource-reload listeners
     */
    CoreClientBackend(ClientGameAdapter adapter, boolean installEventListeners)
    {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.eventListenersEnabled = installEventListeners;
    }

    /**
     * Returns the process-wide backend used by reflective API resolution.
     *
     * <p>The instance is created lazily with {@link ReflectiveClientGameAdapter},
     * installed with event listeners enabled, and cached. This makes reflective
     * resolution through the API facade ready to use without a separate install
     * call. Creation does not load game classes; {@link #install()} only
     * registers bus listeners.
     *
     * @return the shared installed backend, never {@code null}
     */
    public static CoreClientBackend instance()
    {
        CoreClientBackend current = sharedInstance;
        if (current == null)
        {
            synchronized (CoreClientBackend.class)
            {
                current = sharedInstance;
                if (current == null)
                {
                    current = new CoreClientBackend(new ReflectiveClientGameAdapter(), true);
                    sharedInstance = current;
                }
            }
        }
        current.install();
        return current;
    }

    /**
     * Returns the backend installed through {@link #install()} without creating
     * one.
     *
     * @return the installed backend, or {@code null} when none is installed
     */
    static CoreClientBackend installed()
    {
        return installedBackend;
    }

    /**
     * Clears the shared and installed backend references for tests.
     *
     * <p>Any created backend is uninstalled so no global event listener leaks
     * between test cases.
     */
    static void resetInstalledForTests()
    {
        CoreClientBackend current;
        synchronized (CoreClientBackend.class)
        {
            current = sharedInstance;
            sharedInstance = null;
        }
        if (current != null)
        {
            current.uninstall();
        }
        CoreClientBackend installed = installedBackend;
        if (installed != null)
        {
            installed.uninstall();
        }
        synchronized (CoreClientBackend.class)
        {
            installedBackend = null;
        }
    }

    /**
     * Returns the adapter used by this backend.
     *
     * @return the adapter, never {@code null}
     */
    ClientGameAdapter adapter()
    {
        return adapter;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAvailable()
    {
        try
        {
            return adapter.isAvailable() && adapter.canDetectClientThread();
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    /** {@inheritDoc} */
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
            CoreClientHooks.installBridge();
            if (eventListenersEnabled)
            {
                installEventListeners();
            }
        }
        synchronized (CoreClientBackend.class)
        {
            installedBackend = this;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void uninstall()
    {
        List<Registration> snapshot;
        synchronized (lock)
        {
            installed = false;
            preTickHandle = null;
            postTickHandle = null;
            reloadHandle = null;
            snapshot = new ArrayList<>(registrations);
            registrations.clear();
            keyMappings.clear();
            screenFactories.clear();
            hudLayers.clear();
            tooltipProviders.clear();
            chunkListeners.clear();
            preTickListeners.clear();
            postTickListeners.clear();
            reloadListeners.clear();
        }
        CoreClientHooks.uninstallBridge();
        try
        {
            EventBus.GAME.unregister(this);
        }
        catch (Throwable failure)
        {
            log("[weaver] CLIENT EVENT UNREGISTER FAILED reason=" + failure);
        }
        for (Registration registration : snapshot)
        {
            registration.deactivate();
        }
        synchronized (CoreClientBackend.class)
        {
            if (installedBackend == this)
            {
                installedBackend = null;
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public ClientRegistration registerKeyMapping(KeyMappingSpec spec, Runnable action)
    {
        adapter.assertClientThread("registerKeyMapping");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(action, "action");
        synchronized (lock)
        {
            String id = spec.id() + ":" + spec.modId();
            for (KeyEntry entry : keyMappings)
            {
                if (entry.registration.id().equals(id))
                {
                    throw new ClientException("duplicate key mapping id: " + id);
                }
            }
            Object mapping;
            try
            {
                mapping = adapter.createKeyMapping(spec);
            }
            catch (ClientException failure)
            {
                throw failure;
            }
            catch (RuntimeException failure)
            {
                throw new ClientException("failed to create key mapping id=" + id, failure);
            }
            if (mapping == null)
            {
                throw new ClientException("adapter returned a null key mapping for id=" + id);
            }
            KeyEntry entry = new KeyEntry(spec, mapping, action, nextSequence());
            Registration registration = new Registration(id, () -> removeKeyMapping(entry));
            entry.registration = registration;
            keyMappings.add(entry);
            registrations.add(registration);
            return registration;
        }
    }

    /** {@inheritDoc} */
    @Override
    public ClientRegistration registerScreenFactory(ScreenFactory factory)
    {
        adapter.assertClientThread("registerScreenFactory");
        Objects.requireNonNull(factory, "factory");
        synchronized (lock)
        {
            String id = requireId(factory.id(), "screen factory");
            if (screenFactories.containsKey(id))
            {
                throw new ClientException("duplicate screen factory id: " + id);
            }
            ScreenEntry entry = new ScreenEntry(factory, nextSequence());
            Registration registration = new Registration(id,
                    () -> removeScreenFactory(id, entry));
            entry.registration = registration;
            screenFactories.put(id, entry);
            registrations.add(registration);
            return registration;
        }
    }

    /** {@inheritDoc} */
    @Override
    public ClientRegistration registerHudLayer(HudLayer layer)
    {
        adapter.assertClientThread("registerHudLayer");
        Objects.requireNonNull(layer, "layer");
        synchronized (lock)
        {
            String id = requireId(layer.id(), "hud layer");
            for (HudEntry entry : hudLayers)
            {
                if (entry.registration.id().equals(id))
                {
                    throw new ClientException("duplicate hud layer id: " + id);
                }
            }
            HudEntry entry = new HudEntry(layer, layer.order(), nextSequence());
            Registration registration = new Registration(id, () -> removeHudLayer(entry));
            entry.registration = registration;
            hudLayers.add(entry);
            registrations.add(registration);
            return registration;
        }
    }

    /** {@inheritDoc} */
    @Override
    public ClientRegistration registerTooltipProvider(TooltipProvider provider)
    {
        adapter.assertClientThread("registerTooltipProvider");
        Objects.requireNonNull(provider, "provider");
        synchronized (lock)
        {
            String id = requireId(provider.id(), "tooltip provider");
            for (TooltipEntry entry : tooltipProviders)
            {
                if (entry.registration.id().equals(id))
                {
                    throw new ClientException("duplicate tooltip provider id: " + id);
                }
            }
            TooltipEntry entry = new TooltipEntry(provider, nextSequence());
            Registration registration = new Registration(id,
                    () -> removeTooltipProvider(entry));
            entry.registration = registration;
            tooltipProviders.add(entry);
            registrations.add(registration);
            return registration;
        }
    }

    /** {@inheritDoc} */
    @Override
    public ClientRegistration registerChunkListener(ClientChunkListener listener)
    {
        adapter.assertClientThread("registerChunkListener");
        Objects.requireNonNull(listener, "listener");
        synchronized (lock)
        {
            for (ChunkEntry entry : chunkListeners)
            {
                if (entry.listener == listener)
                {
                    throw new ClientException("duplicate chunk listener: "
                            + listener.getClass().getName());
                }
            }
            long order = nextSequence();
            String id = "client-chunk:" + order;
            ChunkEntry entry = new ChunkEntry(listener, order);
            Registration registration = new Registration(id,
                    () -> removeChunkListener(entry));
            entry.registration = registration;
            chunkListeners.add(entry);
            registrations.add(registration);
            return registration;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void chunkLoaded(ClientChunkContext context)
    {
        adapter.assertClientThread("chunkLoaded");
        Objects.requireNonNull(context, "context");
        for (ChunkEntry entry : chunkSnapshot())
        {
            if (!entry.registration.isActive())
            {
                continue;
            }
            try
            {
                entry.listener.onChunkLoad(context);
            }
            catch (Throwable failure)
            {
                log("[weaver] CLIENT CHUNK LISTENER FAILED id=" + entry.registration.id()
                        + " reason=" + failure);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void chunkUnloaded(ClientChunkContext context)
    {
        adapter.assertClientThread("chunkUnloaded");
        Objects.requireNonNull(context, "context");
        for (ChunkEntry entry : chunkSnapshot())
        {
            if (!entry.registration.isActive())
            {
                continue;
            }
            try
            {
                entry.listener.onChunkUnload(context);
            }
            catch (Throwable failure)
            {
                log("[weaver] CLIENT CHUNK LISTENER FAILED id=" + entry.registration.id()
                        + " reason=" + failure);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void blockUpdated(ClientBlockContext context)
    {
        adapter.assertClientThread("blockUpdated");
        Objects.requireNonNull(context, "context");
        for (ChunkEntry entry : chunkSnapshot())
        {
            if (!entry.registration.isActive())
            {
                continue;
            }
            try
            {
                entry.listener.onBlockUpdate(context);
            }
            catch (Throwable failure)
            {
                log("[weaver] CLIENT CHUNK LISTENER FAILED id=" + entry.registration.id()
                        + " reason=" + failure);
            }
        }
    }

    private List<ChunkEntry> chunkSnapshot()
    {
        synchronized (lock)
        {
            return new ArrayList<>(chunkListeners);
        }
    }

    /** {@inheritDoc} */
    @Override
    public ClientRegistration registerTickListener(ClientTickListener listener, boolean pre)
    {
        adapter.assertClientThread("registerTickListener");
        Objects.requireNonNull(listener, "listener");
        synchronized (lock)
        {
            long order = nextSequence();
            String id = (pre ? "client-tick-pre:" : "client-tick-post:") + order;
            TickEntry entry = new TickEntry(listener, order);
            Registration registration = new Registration(id,
                    () -> removeTickListener(entry, pre));
            entry.registration = registration;
            (pre ? preTickListeners : postTickListeners).add(entry);
            registrations.add(registration);
            return registration;
        }
    }

    /** {@inheritDoc} */
    @Override
    public ClientRegistration registerResourceReloadListener(ResourceReloadListener listener)
    {
        adapter.assertClientThread("registerResourceReloadListener");
        Objects.requireNonNull(listener, "listener");
        synchronized (lock)
        {
            long order = nextSequence();
            String id = "client-resource-reload:" + order;
            ReloadEntry entry = new ReloadEntry(listener, order);
            Registration registration = new Registration(id,
                    () -> removeReloadListener(entry));
            entry.registration = registration;
            reloadListeners.add(entry);
            registrations.add(registration);
            return registration;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void openScreen(String screenFactoryId)
    {
        adapter.assertClientThread("openScreen");
        if (screenFactoryId == null || screenFactoryId.isBlank())
        {
            log("[weaver] CLIENT SCREEN IGNORED reason=blank-id");
            return;
        }
        ScreenEntry entry;
        synchronized (lock)
        {
            entry = screenFactories.get(screenFactoryId);
        }
        if (entry == null || !entry.registration.isActive())
        {
            log("[weaver] CLIENT SCREEN UNKNOWN id=" + screenFactoryId);
            return;
        }
        try
        {
            Object screen = entry.factory.createScreen(adapter.minecraft(),
                    adapter.currentScreen());
            if (screen != null)
            {
                adapter.openScreen(screen);
            }
        }
        catch (Throwable failure)
        {
            log("[weaver] CLIENT SCREEN OPEN FAILED id=" + screenFactoryId
                    + " reason=" + failure);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void renderHud(HudRenderContext context)
    {
        adapter.assertClientThread("renderHud");
        Objects.requireNonNull(context, "context");
        List<HudEntry> snapshot;
        synchronized (lock)
        {
            snapshot = new ArrayList<>(hudLayers);
        }
        snapshot.removeIf(entry -> !entry.registration.isActive());
        snapshot.sort(Comparator.comparingInt((HudEntry entry) -> entry.order)
                .thenComparingLong(entry -> entry.sequence));
        for (HudEntry entry : snapshot)
        {
            if (!entry.registration.isActive())
            {
                continue;
            }
            try
            {
                entry.layer.render(context);
            }
            catch (Throwable failure)
            {
                log("[weaver] CLIENT HUD LAYER FAILED id=" + entry.registration.id()
                        + " reason=" + failure);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<String> tooltipLines(TooltipContext context)
    {
        adapter.assertClientThread("tooltipLines");
        Objects.requireNonNull(context, "context");
        List<TooltipEntry> snapshot;
        synchronized (lock)
        {
            snapshot = new ArrayList<>(tooltipProviders);
        }
        List<String> collected = new ArrayList<>();
        for (TooltipEntry entry : snapshot)
        {
            if (!entry.registration.isActive())
            {
                continue;
            }
            try
            {
                entry.provider.append(context, collected);
            }
            catch (Throwable failure)
            {
                log("[weaver] CLIENT TOOLTIP PROVIDER FAILED id="
                        + entry.registration.id() + " reason=" + failure);
            }
        }
        List<String> result = new ArrayList<>(collected.size());
        for (String line : collected)
        {
            if (line != null)
            {
                result.add(line);
            }
        }
        return List.copyOf(result);
    }

    private void installEventListeners()
    {
        EventListener<ClientTickEvent.Pre> pre = event -> handleTick(event, true);
        EventListener<ClientTickEvent.Post> post = event -> handleTick(event, false);
        EventListener<ResourceReloadEvent> reload = this::handleResourceReload;
        try
        {
            preTickHandle = EventBus.GAME.addListener(ClientTickEvent.Pre.class, this, pre,
                    EventPriority.NORMAL, false);
        }
        catch (Throwable failure)
        {
            log("[weaver] CLIENT PRE TICK LISTENER FAILED reason=" + failure);
        }
        try
        {
            postTickHandle = EventBus.GAME.addListener(ClientTickEvent.Post.class, this, post,
                    EventPriority.NORMAL, false);
        }
        catch (Throwable failure)
        {
            log("[weaver] CLIENT POST TICK LISTENER FAILED reason=" + failure);
        }
        try
        {
            reloadHandle = EventBus.GAME.addListener(ResourceReloadEvent.class, this, reload,
                    EventPriority.NORMAL, false);
        }
        catch (Throwable failure)
        {
            log("[weaver] CLIENT RELOAD LISTENER FAILED reason=" + failure);
        }
    }

    private void handleTick(ClientTickEvent event, boolean pre)
    {
        List<KeyEntry> keys = null;
        List<TickEntry> listeners;
        synchronized (lock)
        {
            if (pre)
            {
                keys = new ArrayList<>(keyMappings);
            }
            listeners = new ArrayList<>(pre ? preTickListeners : postTickListeners);
        }
        if (pre && keys != null)
        {
            pollKeyMappings(keys);
        }
        if (listeners.isEmpty())
        {
            return;
        }
        ClientTickContext context = new ClientTickContext(event.tick(), pre, clientInstance());
        for (TickEntry entry : listeners)
        {
            if (!entry.registration.isActive())
            {
                continue;
            }
            try
            {
                entry.listener.onTick(context);
            }
            catch (Throwable failure)
            {
                log("[weaver] CLIENT TICK LISTENER FAILED id=" + entry.registration.id()
                        + " reason=" + failure);
            }
        }
    }

    private void pollKeyMappings(List<KeyEntry> keys)
    {
        for (KeyEntry entry : keys)
        {
            if (!entry.registration.isActive())
            {
                continue;
            }
            boolean clicked;
            try
            {
                clicked = adapter.consumeClick(entry.mapping);
            }
            catch (Throwable failure)
            {
                log("[weaver] CLIENT KEY POLL FAILED id=" + entry.registration.id()
                        + " reason=" + failure);
                continue;
            }
            if (!clicked)
            {
                continue;
            }
            int required = entry.spec.modifiers();
            if (required != 0)
            {
                int current;
                try
                {
                    current = adapter.currentModifiers();
                }
                catch (Throwable failure)
                {
                    log("[weaver] CLIENT KEY MODIFIER FAILED id=" + entry.registration.id()
                            + " reason=" + failure);
                    continue;
                }
                if (!KeyModifiers.matches(required, current))
                {
                    continue;
                }
            }
            try
            {
                entry.action.run();
            }
            catch (Throwable failure)
            {
                log("[weaver] CLIENT KEY ACTION FAILED id=" + entry.registration.id()
                        + " reason=" + failure);
            }
        }
    }

    private void handleResourceReload(ResourceReloadEvent event)
    {
        if (event.side() != Side.CLIENT)
        {
            return;
        }
        ResourceReloadContext.Phase phase = switch (event.phase())
        {
            case START -> ResourceReloadContext.Phase.START;
            case FINISHED -> ResourceReloadContext.Phase.FINISHED;
            case FAILED -> ResourceReloadContext.Phase.FAILED;
        };
        ResourceReloadContext context = new ResourceReloadContext(phase, event.source(), true);
        List<ReloadEntry> snapshot;
        synchronized (lock)
        {
            snapshot = new ArrayList<>(reloadListeners);
        }
        for (ReloadEntry entry : snapshot)
        {
            if (!entry.registration.isActive())
            {
                continue;
            }
            try
            {
                entry.listener.onReload(context);
            }
            catch (Throwable failure)
            {
                log("[weaver] CLIENT RELOAD LISTENER FAILED id=" + entry.registration.id()
                        + " reason=" + failure);
            }
        }
    }

    private Object clientInstance()
    {
        try
        {
            return adapter.minecraft();
        }
        catch (Throwable failure)
        {
            log("[weaver] CLIENT INSTANCE FAILED reason=" + failure);
            return null;
        }
    }

    private void removeKeyMapping(KeyEntry entry)
    {
        synchronized (lock)
        {
            keyMappings.remove(entry);
        }
        try
        {
            adapter.removeKeyMapping(entry.mapping);
        }
        catch (Throwable failure)
        {
            log("[weaver] CLIENT KEY REMOVE FAILED id=" + entry.registration.id()
                    + " reason=" + failure);
        }
    }

    private void removeScreenFactory(String id, ScreenEntry entry)
    {
        synchronized (lock)
        {
            screenFactories.remove(id, entry);
        }
    }

    private void removeHudLayer(HudEntry entry)
    {
        synchronized (lock)
        {
            hudLayers.remove(entry);
        }
    }

    private void removeTooltipProvider(TooltipEntry entry)
    {
        synchronized (lock)
        {
            tooltipProviders.remove(entry);
        }
    }

    private void removeChunkListener(ChunkEntry entry)
    {
        synchronized (lock)
        {
            chunkListeners.remove(entry);
        }
    }

    private void removeTickListener(TickEntry entry, boolean pre)
    {
        synchronized (lock)
        {
            (pre ? preTickListeners : postTickListeners).remove(entry);
        }
    }

    private void removeReloadListener(ReloadEntry entry)
    {
        synchronized (lock)
        {
            reloadListeners.remove(entry);
        }
    }

    private long nextSequence()
    {
        return sequence++;
    }

    private static String requireId(String id, String type)
    {
        if (id == null || id.isBlank())
        {
            throw new ClientException(type + " id must not be blank");
        }
        return id;
    }

    private static void log(String message)
    {
        try
        {
            LifecycleHooks.logLine(message);
        }
        catch (Throwable ignored)
        {
        }
    }

    private final class Registration implements ClientRegistration
    {
        private final String id;
        private final Runnable remover;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Registration(String id, Runnable remover)
        {
            this.id = id;
            this.remover = remover;
        }

        @Override
        public String id()
        {
            return id;
        }

        @Override
        public boolean isActive()
        {
            return active.get();
        }

        @Override
        public void unregister()
        {
            if (!active.compareAndSet(true, false))
            {
                return;
            }
            synchronized (lock)
            {
                registrations.remove(this);
            }
            try
            {
                remover.run();
            }
            catch (Throwable failure)
            {
                log("[weaver] CLIENT UNREGISTER FAILED id=" + id + " reason=" + failure);
            }
        }

        private void deactivate()
        {
            active.set(false);
        }
    }

    private static final class KeyEntry
    {
        private final KeyMappingSpec spec;
        private final Object mapping;
        private final Runnable action;
        private final long sequence;
        private Registration registration;

        private KeyEntry(KeyMappingSpec spec, Object mapping, Runnable action, long sequence)
        {
            this.spec = spec;
            this.mapping = mapping;
            this.action = action;
            this.sequence = sequence;
        }
    }

    private static final class ScreenEntry
    {
        private final ScreenFactory factory;
        private final long sequence;
        private Registration registration;

        private ScreenEntry(ScreenFactory factory, long sequence)
        {
            this.factory = factory;
            this.sequence = sequence;
        }
    }

    private static final class HudEntry
    {
        private final HudLayer layer;
        private final int order;
        private final long sequence;
        private Registration registration;

        private HudEntry(HudLayer layer, int order, long sequence)
        {
            this.layer = layer;
            this.order = order;
            this.sequence = sequence;
        }
    }

    private static final class TooltipEntry
    {
        private final TooltipProvider provider;
        private final long sequence;
        private Registration registration;

        private TooltipEntry(TooltipProvider provider, long sequence)
        {
            this.provider = provider;
            this.sequence = sequence;
        }
    }

    private static final class ChunkEntry
    {
        private final ClientChunkListener listener;
        private final long sequence;
        private Registration registration;

        private ChunkEntry(ClientChunkListener listener, long sequence)
        {
            this.listener = listener;
            this.sequence = sequence;
        }
    }

    private static final class TickEntry
    {
        private final ClientTickListener listener;
        private final long sequence;
        private Registration registration;

        private TickEntry(ClientTickListener listener, long sequence)
        {
            this.listener = listener;
            this.sequence = sequence;
        }
    }

    private static final class ReloadEntry
    {
        private final ResourceReloadListener listener;
        private final long sequence;
        private Registration registration;

        private ReloadEntry(ResourceReloadListener listener, long sequence)
        {
            this.listener = listener;
            this.sequence = sequence;
        }
    }
}
