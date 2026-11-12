package net.weaver.core;

import net.weaver.api.ModContext;
import net.weaver.api.WeaverMod;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.events.ModLifecycleEvent;
import net.weaver.api.event.events.RegisterEvent;
import net.weaver.api.event.events.RegistryFreezeEvent;
import net.weaver.api.registry.DataRegistryManager;
import net.weaver.api.registry.RegistryKey;
import net.weaver.api.registry.RegistryManager;
import net.weaver.core.event.AutoSubscriberScanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** The loader phases, called from the game's own entry points. */
public final class LifecycleHooks {

    private static final List<Loaded> LOADED = new ArrayList<>();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final AtomicBoolean COMMON = new AtomicBoolean();
    private static final AtomicBoolean CLIENT_READY = new AtomicBoolean();
    private static final AtomicBoolean SERVER_READY = new AtomicBoolean();
    private static final AtomicBoolean CLIENT_TICKED = new AtomicBoolean();
    private static final AtomicBoolean SERVER_TICKED = new AtomicBoolean();
    private static final AtomicBoolean COMPLETE = new AtomicBoolean();
    private static volatile boolean expectedClient;
    private static volatile boolean expectedServer;
    private static volatile String processSide = "client";
    private static ClassLoader modLoader;
    private static Consumer<String> log = line -> { };
    private static volatile List<LoadedMod> modView = List.of();

    private LifecycleHooks() {
    }

    public static void install(List<ModJar> mods, ClassLoader loader, String side,
                               boolean integratedServerExpected, Path configRoot,
                               Path dataRoot, Consumer<String> logger) {
        modLoader = loader;
        processSide = side;
        log = logger == null ? line -> { } : logger;
        expectedClient = "client".equals(side);
        expectedServer = "server".equals(side) || integratedServerExpected;
        LOADED.clear();
        for (ModJar mod : mods) {
            Path configDir = configRoot.resolve(mod.metadata().id());
            Path dataDir = dataRoot.resolve(mod.metadata().id());
            try {
                Files.createDirectories(configDir);
                Files.createDirectories(dataDir);
            } catch (IOException e) {
                logFailure("[weaver] MOD DIR FAILED id=" + mod.metadata().id() + " reason=" + e, e);
            }
            EventBus localBus = EventBus.local(mod.metadata().id());
            LOADED.add(new Loaded(mod, new Context(mod.metadata().id(), side, configDir, dataDir,
                    log, LogicalSide.BOTH, localBus)));
        }
    }

    public static void onInitialize() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        List<Loaded> instantiated = new ArrayList<>();
        for (Loaded loaded : LOADED) {
            try {
                Class<?> type = Class.forName(loaded.mod.metadata().mainClass(), true, modLoader);
                Object instance = type.getDeclaredConstructor().newInstance();
                if (instance instanceof WeaverMod weaver) {
                    loaded.instance = weaver;
                    instantiated.add(loaded);
                } else {
                    log.accept("[weaver] MOD ENTRY not-a-WeaverMod id=" + loaded.mod.metadata().id());
                }
            } catch (Throwable failure) {
                logFailure("[weaver] MOD LOAD FAILED id=" + loaded.mod.metadata().id()
                        + " reason=" + failure, failure);
            }
        }
        LOADED.clear();
        LOADED.addAll(instantiated);
        modView = buildView();

        for (Loaded loaded : LOADED) {
            try {
                EventBus.GAME.register(loaded.instance);
            } catch (Throwable failure) {
                logFailure("[weaver] MOD SUBSCRIBE FAILED id=" + loaded.mod.metadata().id()
                        + " reason=" + failure, failure);
            }
        }

        for (Loaded loaded : LOADED) {
            try {
                loaded.instance.onInitialize(loaded.context);
                postLifecycle(loaded, ModLifecycleEvent.Phase.INITIALIZE);
                log.accept("[weaver] MOD INIT id=" + loaded.mod.metadata().id());
            } catch (Throwable failure) {
                logFailure("[weaver] MOD INIT FAILED id=" + loaded.mod.metadata().id()
                        + " reason=" + failure, failure);
            }
        }

        for (Loaded loaded : LOADED) {
            try {
                AutoSubscriberScanner.scan(loaded.mod, processSide, expectedClient, expectedServer,
                        modLoader, log, loaded.instance.getClass(), loaded.localBus);
            } catch (Throwable failure) {
                logFailure("[weaver] MOD SCAN FAILED id=" + loaded.mod.metadata().id()
                        + " reason=" + failure, failure);
            }
        }
    }

    public static void onRegister() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        RegistryManager.beginRegistration();
        try {
            DataRegistryManager.beginRegistration();
            try {
                RegistryManager.flushAll();
                fireRegistryEvents(true);
                call("REGISTER", WeaverMod::onRegister, ModLifecycleEvent.Phase.REGISTER);
                RegistryManager.flushAll();
            } finally {
                try {
                    RegistryManager.markFrozen();
                } catch (Throwable failure) {
                    log.accept("[weaver] REGISTRY FREEZE FAILED reason=" + failure);
                }
                try {
                    fireRegistryEvents(false);
                } catch (Throwable failure) {
                    log.accept("[weaver] REGISTRY FREEZE EVENT FAILED reason=" + failure);
                }
            }
        } finally {
            try {
                DataRegistryManager.endRegistration();
            } catch (Throwable failure) {
                log.accept("[weaver] DATA REGISTRY PHASE FAILED reason=" + failure);
            }
            RegistryManager.endRegistration();
        }
    }

    public static void onCommonSetup() {
        if (COMMON.compareAndSet(false, true)) {
            // The game bootstrap has filled the vanilla registries since onInitialize,
            // so registry views must not serve caches filled before it.
            net.weaver.api.registry.RegistryManager.invalidateCaches();
            call("COMMON", WeaverMod::onCommonSetup, ModLifecycleEvent.Phase.COMMON_SETUP);
        }
    }

    public static void onClientSetup() {
        if (CLIENT_READY.compareAndSet(false, true)) {
            call("CLIENT", WeaverMod::onClientSetup, ModLifecycleEvent.Phase.CLIENT_SETUP);
        }
    }

    public static void onServerSetup() {
        if (SERVER_READY.compareAndSet(false, true)) {
            call("SERVER", WeaverMod::onServerSetup, ModLifecycleEvent.Phase.SERVER_SETUP);
        }
    }

    /** Called from the first client tick; completes only when every expected side is ready. */
    public static void onClientTick() {
        CLIENT_TICKED.set(true);
        maybeComplete();
    }

    /** Called from the first server tick; completes only when every expected side is ready. */
    public static void onServerTick() {
        SERVER_TICKED.set(true);
        maybeComplete();
    }

    public static List<LoadedMod> mods() {
        return modView;
    }

    public static int modCount() {
        return LOADED.size();
    }

    public static void logLine(String message) {
        log.accept(message);
    }

    public static String processSide() {
        return processSide;
    }

    /**
     * True when this process hosts a logical client. Set from the agent's
     * {@code side} option during {@link #install}.
     */
    public static boolean clientAvailable() {
        return expectedClient;
    }

    /**
     * True when this process hosts a logical server. This is true on a
     * dedicated server and on a client that starts an integrated server.
     */
    public static boolean serverAvailable() {
        return expectedServer;
    }

    public static void reset() {
        LOADED.clear();
        modView = List.of();
        INITIALIZED.set(false);
        REGISTERED.set(false);
        COMMON.set(false);
        CLIENT_READY.set(false);
        SERVER_READY.set(false);
        CLIENT_TICKED.set(false);
        SERVER_TICKED.set(false);
        COMPLETE.set(false);
        processSide = "client";
        expectedClient = false;
        expectedServer = false;
        modLoader = null;
        log = line -> { };
        RegistryManager.reset();
    }

    private static List<LoadedMod> buildView() {
        List<LoadedMod> view = new ArrayList<>(LOADED.size());
        for (Loaded loaded : LOADED) {
            view.add(new LoadedMod(loaded.mod.metadata().id(), loaded.instance, loaded.context,
                    loaded.clientContext, loaded.serverContext, loaded.localBus));
        }
        return List.copyOf(view);
    }

    private static void fireRegistryEvents(boolean registering) {
        EventBus bus = EventBus.GAME;
        boolean listening = registering
                ? bus.hasListeners(RegisterEvent.class)
                : bus.hasListeners(RegistryFreezeEvent.class);
        if (!listening) {
            return;
        }
        List<String> ids;
        try {
            ids = RegistryManager.registryIds();
        } catch (Throwable failure) {
            log.accept("[weaver] REGISTRY DISCOVERY FAILED reason=" + failure);
            return;
        }
        for (String id : ids) {
            try {
                RegistryKey<?> key = RegistryKey.parse(id);
                if (registering) {
                    bus.post(new RegisterEvent(key).setLogicalSide(LogicalSide.BOTH));
                } else {
                    bus.post(new RegistryFreezeEvent(key).setLogicalSide(LogicalSide.BOTH));
                }
            } catch (Throwable failure) {
                logFailure("[weaver] REGISTRY EVENT FAILED id=" + id + " reason=" + failure, failure);
            }
        }
    }

    private static void maybeComplete() {
        if (COMPLETE.get()) {
            return;
        }
        if (expectedClient && (!CLIENT_READY.get() || !CLIENT_TICKED.get())) {
            return;
        }
        boolean waitForServer = serverRequiredForCompletion();
        if (waitForServer && (!SERVER_READY.get() || !SERVER_TICKED.get())) {
            return;
        }
        if (COMPLETE.compareAndSet(false, true)) {
            call("COMPLETE", WeaverMod::onLoadComplete, ModLifecycleEvent.Phase.LOAD_COMPLETE);
            log.accept("[weaver] LIFECYCLE COMPLETE expected-client=" + expectedClient
                    + " expected-server=" + waitForServer);
        }
    }

    /**
     * A dedicated server always waits for its own setup and first tick. A
     * client may host an integrated server, but it does not start one until a
     * world is opened and a client that only joins remote servers never starts
     * one, so the client waits for the server side only once integrated
     * server setup has actually begun.
     */
    private static boolean serverRequiredForCompletion() {
        if (!expectedServer) {
            return false;
        }
        if (!expectedClient) {
            return true;
        }
        return SERVER_READY.get();
    }

    private static void call(String phase, PhaseCall call, ModLifecycleEvent.Phase eventPhase) {
        LogicalSide logicalSide = logicalSideOf(eventPhase);
        for (Loaded loaded : LOADED) {
            if (loaded.instance == null) {
                continue;
            }
            try {
                call.accept(loaded.instance, loaded.context(logicalSide));
                postLifecycle(loaded, eventPhase);
            } catch (Throwable failure) {
                logFailure("[weaver] MOD " + phase + " FAILED id=" + loaded.mod.metadata().id()
                        + " reason=" + failure, failure);
            }
        }
    }

    /** Most stack lines written for one failure; the rest are counted. */
    /** Frames shown for the failure itself and for each of its causes. */
    private static final int FRAMES_PER_THROWABLE = 16;

    /** Causes followed at most (a cycle or a very long chain stops here). */
    private static final int MAX_CAUSES = 8;

    /**
     * Logs a mod's failure: the one-line summary as before, then where it
     * happened. Each throwable in the cause chain gets its header and its first
     * frames (frames shared with the throwable around it are left out, as the
     * JVM does), so the cause, which usually names the real problem, is never
     * cut off by a long outer trace.
     */
    static void logFailure(String line, Throwable failure) {
        log.accept(line);
        if (failure == null) {
            return;
        }
        java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        StackTraceElement[] enclosing = new StackTraceElement[0];
        Throwable current = failure;
        int depth = 0;
        while (current != null && seen.add(current)) {
            if (depth > MAX_CAUSES) {
                log.accept("[weaver]   ... further causes left out");
                break;
            }
            StackTraceElement[] frames = current.getStackTrace();
            if (frames == null) {
                frames = new StackTraceElement[0];
            }
            if (depth > 0) {
                log.accept("[weaver]   Caused by: " + current);
            }
            int unique = frames.length;
            for (int a = frames.length - 1, b = enclosing.length - 1;
                    depth > 0 && a >= 0 && b >= 0 && frames[a].equals(enclosing[b]); a--, b--) {
                unique--;
            }
            int shown = Math.min(unique, FRAMES_PER_THROWABLE);
            for (int i = 0; i < shown; i++) {
                log.accept("[weaver]     at " + frames[i]);
            }
            if (frames.length > shown) {
                log.accept("[weaver]     ... " + (frames.length - shown) + " more");
            }
            int suppressed = current.getSuppressed().length;
            if (suppressed > 0) {
                log.accept("[weaver]     (" + suppressed + " suppressed: "
                        + current.getSuppressed()[0] + (suppressed > 1 ? ", ..." : "") + ")");
            }
            enclosing = frames;
            current = current.getCause();
            depth++;
        }
    }

    private static void postLifecycle(Loaded loaded, ModLifecycleEvent.Phase phase) {
        try {
            LogicalSide logicalSide = logicalSideOf(phase);
            ModLifecycleEvent event = new ModLifecycleEvent(loaded.context(logicalSide), phase);
            event.setLogicalSide(logicalSide);
            loaded.localBus.post(event);
        } catch (Throwable failure) {
            logFailure("[weaver] MOD EVENT FAILED id=" + loaded.mod.metadata().id()
                    + " phase=" + phase + " reason=" + failure, failure);
        }
    }

    private static LogicalSide logicalSideOf(ModLifecycleEvent.Phase phase) {
        return switch (phase) {
            case CLIENT_SETUP -> LogicalSide.CLIENT;
            case SERVER_SETUP -> LogicalSide.SERVER;
            default -> LogicalSide.BOTH;
        };
    }

    @FunctionalInterface
    private interface PhaseCall {
        void accept(WeaverMod mod, ModContext context);
    }

    /**
     * A loaded mod as exposed through {@link #mods()}.
     *
     * @param id            the stable mod id
     * @param mod           the mod entrypoint instance
     * @param context       the side-neutral context
     * @param clientContext the logical-client context
     * @param serverContext the logical-server context
     * @param localBus      the mod's local event bus
     */
    public record LoadedMod(String id, WeaverMod mod, ModContext context,
                            ModContext clientContext, ModContext serverContext,
                            EventBus localBus) {

        /** Returns the context for a logical side; {@code null} or BOTH returns the base context. */
        public ModContext context(LogicalSide logicalSide) {
            if (logicalSide == null) {
                return context;
            }
            return switch (logicalSide) {
                case CLIENT -> clientContext;
                case SERVER -> serverContext;
                case BOTH -> context;
            };
        }

        /**
         * The mod's local event bus, the bus returned by
         * {@link EventBus#local(String)} for this mod.
         *
         * @return the local bus shared by all three contexts, never {@code null}
         */
        public EventBus localBus() {
            return localBus;
        }
    }

    private static final class Loaded {
        private final ModJar mod;
        private final Context context;
        private final Context clientContext;
        private final Context serverContext;
        private final EventBus localBus;
        private WeaverMod instance;

        private Loaded(ModJar mod, Context context) {
            this.mod = mod;
            this.context = context;
            this.clientContext = context.withLogicalSide(LogicalSide.CLIENT);
            this.serverContext = context.withLogicalSide(LogicalSide.SERVER);
            this.localBus = context.modEvents();
        }

        private ModContext context(LogicalSide logicalSide) {
            if (logicalSide == null) {
                return context;
            }
            return switch (logicalSide) {
                case CLIENT -> clientContext;
                case SERVER -> serverContext;
                case BOTH -> context;
            };
        }
    }

    private record Context(String id, String side, Path configDir, Path dataDir,
                           Consumer<String> logger, LogicalSide logicalSide,
                           EventBus localBus) implements ModContext {

        @Override
        public void log(String message) {
            logger.accept("[weaver] MOD " + id + " " + message);
        }

        /** {@inheritDoc} */
        @Override
        public EventBus modEvents() {
            return localBus;
        }

        /** {@inheritDoc} */
        @Override
        public EventBus globalModEvents() {
            return EventBus.MOD;
        }

        private Context withLogicalSide(LogicalSide logicalSide) {
            if (this.logicalSide == logicalSide) {
                return this;
            }
            return new Context(id, side, configDir, dataDir, logger, logicalSide, localBus);
        }
    }
}
