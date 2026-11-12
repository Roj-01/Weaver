package net.weaver.api.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class RegistryManager
{
    private static final Object LOCK = new Object();
    private static final Map<String, RegistryImpl<?>> registries = new LinkedHashMap<>();
    private static final List<DeferredRegister<?>> deferredRegisters = new ArrayList<>();
    private static RegistryBackend backend;
    private static Consumer<String> logConsumer;
    private static boolean registrationPhase;

    private RegistryManager()
    {
    }

    /**
     * The outcome of one {@link #markFrozenWithReport()} pass. The report is
     * immutable. A freeze is complete when discovery succeeded and no registry
     * failed; entries that succeeded remain frozen even when the report is not
     * complete.
     */
    public record FreezeReport(int frozenCount, List<String> frozenIds,
                               Map<String, String> failures, boolean discoveryFailed)
    {
        public FreezeReport
        {
            frozenIds = List.copyOf(frozenIds);
            failures = Map.copyOf(failures);
        }

        public boolean isComplete()
        {
            return !discoveryFailed && failures.isEmpty();
        }
    }

    public static void install(RegistryBackend backend, Consumer<String> log)
    {
        Objects.requireNonNull(backend, "backend");
        synchronized (LOCK)
        {
            RegistryManager.backend = backend;
            logConsumer = log != null ? log : message -> {
            };
            registries.clear();
        }
    }

    public static boolean isInstalled()
    {
        synchronized (LOCK)
        {
            return backend != null;
        }
    }

    public static RegistryBackend backend()
    {
        synchronized (LOCK)
        {
            return backend;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> RegistryView<T> registry(RegistryKey<?> key)
    {
        Objects.requireNonNull(key, "key");
        synchronized (LOCK)
        {
            if (backend == null)
            {
                throw new RegistryException("RegistryView backend is not installed");
            }
            if (!availableLocked())
            {
                throw new RegistryException("RegistryView backend is not available");
            }
            if (!registryIdsLocked().contains(key.id()))
            {
                throw new RegistryException("Unknown registry: " + key.id());
            }
            return (RegistryView<T>) registryLocked(key);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> Optional<RegistryView<T>> findRegistry(RegistryKey<?> key)
    {
        Objects.requireNonNull(key, "key");
        synchronized (LOCK)
        {
            if (!availableLocked() || !registryIdsLocked().contains(key.id()))
            {
                return Optional.empty();
            }
            return Optional.of((RegistryView<T>) registryLocked(key));
        }
    }

    public static List<String> registryIds()
    {
        synchronized (LOCK)
        {
            if (!availableLocked())
            {
                return List.of();
            }
            return List.copyOf(registryIdsLocked());
        }
    }

    public static void track(DeferredRegister<?> register)
    {
        Objects.requireNonNull(register, "register");
        synchronized (LOCK)
        {
            if (!deferredRegisters.contains(register))
            {
                deferredRegisters.add(register);
            }
        }
    }

    /**
     * Flushes every tracked deferred register. A registry lookup failure only
     * fails the entries that target that registry; later groups still flush.
     * When no backend is installed, every tracked entry is failed and logged
     * instead of throwing.
     */
    public static void flushAll()
    {
        try
        {
            flushAllDeferred();
        }
        finally
        {
            // Flushed entries changed the native registries behind every view.
            invalidateCaches();
        }
    }

    private static void flushAllDeferred()
    {
        final RegistryBackend currentBackend;
        final Consumer<String> logger;
        final List<DeferredRegister<?>> tracked;
        synchronized (LOCK)
        {
            currentBackend = backend;
            logger = logConsumer;
            tracked = new ArrayList<>(deferredRegisters);
        }
        if (tracked.isEmpty())
        {
            return;
        }
        final Consumer<String> log = logger != null ? logger : message -> {
        };
        if (currentBackend == null)
        {
            for (DeferredRegister<?> deferred : tracked)
            {
                flushWithoutRegistry(deferred, log);
            }
            return;
        }
        final Map<RegistryKey<?>, List<DeferredRegister<?>>> grouped = new LinkedHashMap<>();
        for (DeferredRegister<?> deferred : tracked)
        {
            final RegistryKey<?> key;
            try
            {
                key = deferred.registryKey();
            }
            catch (Throwable failure)
            {
                safeLog(log, "Failed to read deferred register registry key: " + describe(failure));
                flushWithoutRegistry(deferred, log);
                continue;
            }
            if (key == null)
            {
                safeLog(log, "Deferred register has no registry key; failing its entries");
                flushWithoutRegistry(deferred, log);
                continue;
            }
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(deferred);
        }
        for (Map.Entry<RegistryKey<?>, List<DeferredRegister<?>>> group : grouped.entrySet())
        {
            RegistryView<?> registry = null;
            try
            {
                registry = registry(group.getKey());
            }
            catch (Throwable failure)
            {
                safeLog(log, "Failed to open registry " + group.getKey().id()
                        + " for deferred registration: " + describe(failure));
            }
            for (DeferredRegister<?> deferred : group.getValue())
            {
                try
                {
                    flushOne(deferred, registry, log);
                }
                catch (Throwable failure)
                {
                    safeLog(log, "Failed to flush deferred register " + group.getKey().id()
                            + ": " + describe(failure));
                    flushWithoutRegistry(deferred, log);
                }
            }
        }
    }

    /**
     * Freezes every known registry. Failures are logged and isolated: an
     * unparseable registry id, a backend failure, or one registry that cannot
     * freeze never prevents the remaining registries from freezing.
     */
    public static void markFrozen()
    {
        markFrozenWithReport();
    }

    /**
     * Same as {@link #markFrozen()} but returns a diagnostic report. Use this
     * from tests or loader diagnostics; production callers can keep using
     * {@link #markFrozen()}.
     */
    public static FreezeReport markFrozenWithReport()
    {
        final RegistryBackend currentBackend;
        final Consumer<String> logger;
        final Set<String> ids = new LinkedHashSet<>();
        synchronized (LOCK)
        {
            currentBackend = backend;
            logger = logConsumer;
            ids.addAll(registries.keySet());
        }
        final List<String> frozenIds = new ArrayList<>();
        final Map<String, String> failures = new LinkedHashMap<>();
        boolean discoveryFailed = false;
        if (currentBackend == null)
        {
            safeLog(logger, "Registry freeze skipped: backend is not installed");
            return new FreezeReport(0, frozenIds, failures, false);
        }
        try
        {
            final Set<String> discovered = currentBackend.registryIds();
            if (discovered == null)
            {
                discoveryFailed = true;
                safeLog(logger, "[weaver] REGISTRY FREEZE FAILED discovery reason="
                        + "backend returned null; continuing with known registries");
            }
            else
            {
                for (String id : discovered)
                {
                    if (id != null)
                    {
                        ids.add(id);
                    }
                }
            }
        }
        catch (Throwable failure)
        {
            discoveryFailed = true;
            safeLog(logger, "[weaver] REGISTRY FREEZE FAILED discovery reason="
                    + describe(failure) + "; continuing with known registries");
        }
        for (String id : ids)
        {
            if (id == null)
            {
                continue;
            }
            final RegistryKey<?> key;
            try
            {
                key = RegistryKey.parse(id);
            }
            catch (Throwable failure)
            {
                failures.put(id, "invalid registry id: " + describe(failure));
                safeLog(logger, "[weaver] REGISTRY FREEZE FAILED id=" + id
                        + " reason=invalid registry id: " + describe(failure) + "; continuing");
                continue;
            }
            final RegistryImpl<?> registry;
            try
            {
                synchronized (LOCK)
                {
                    if (backend != currentBackend)
                    {
                        failures.put(id, "backend changed during freeze");
                        safeLog(logger, "[weaver] REGISTRY FREEZE FAILED id=" + id
                                + " reason=backend changed during freeze; continuing");
                        continue;
                    }
                    registry = registries.computeIfAbsent(id,
                            ignored -> new RegistryImpl<>(currentBackend, key));
                }
            }
            catch (Throwable failure)
            {
                failures.put(id, "registry creation failed: " + describe(failure));
                safeLog(logger, "[weaver] REGISTRY FREEZE FAILED id=" + id
                        + " reason=registry creation failed: " + describe(failure) + "; continuing");
                continue;
            }
            try
            {
                registry.freeze();
                frozenIds.add(id);
            }
            catch (Throwable failure)
            {
                failures.put(id, describe(failure));
                safeLog(logger, "[weaver] REGISTRY FREEZE FAILED id=" + id + " reason="
                        + describe(failure) + "; continuing");
            }
        }
        if (!failures.isEmpty() || discoveryFailed)
        {
            safeLog(logger, "Registry freeze completed with " + frozenIds.size() + " frozen and "
                    + failures.size() + " failed"
                    + (discoveryFailed ? " (registry discovery failed)" : ""));
        }
        return new FreezeReport(frozenIds.size(), frozenIds, failures, discoveryFailed);
    }

    public static void beginRegistration()
    {
        synchronized (LOCK)
        {
            registrationPhase = true;
        }
        invalidateCaches();
    }

    /**
     * Drops every registry view's read caches.
     *
     * <p>Views cache key, value, and entry lookups. Until a registry is frozen
     * the native registry can change outside the view (the vanilla bootstrap,
     * other mods, direct game calls), so the loader calls this at the points
     * where that happens: after the game bootstrap, when registration begins, and
     * after each deferred flush. A cache filled early, for example from
     * {@code onInitialize}, therefore never hides later entries.</p>
     */
    public static void invalidateCaches()
    {
        final List<RegistryImpl<?>> views;
        synchronized (LOCK)
        {
            views = new ArrayList<>(registries.values());
        }
        for (RegistryImpl<?> view : views)
        {
            view.invalidateCaches();
        }
    }

    public static void endRegistration()
    {
        synchronized (LOCK)
        {
            registrationPhase = false;
        }
    }

    public static boolean isRegistrationPhase()
    {
        synchronized (LOCK)
        {
            return registrationPhase;
        }
    }

    public static void log(String message)
    {
        Objects.requireNonNull(message, "message");
        final Consumer<String> logger;
        synchronized (LOCK)
        {
            logger = logConsumer;
        }
        if (logger != null)
        {
            safeLog(logger, message);
        }
    }

    public static void reset()
    {
        synchronized (LOCK)
        {
            backend = null;
            logConsumer = null;
            registries.clear();
            deferredRegisters.clear();
            registrationPhase = false;
        }
    }

    private static boolean availableLocked()
    {
        if (backend == null)
        {
            return false;
        }
        try
        {
            return backend.isAvailable();
        }
        catch (Throwable failure)
        {
            return false;
        }
    }

    private static Set<String> registryIdsLocked()
    {
        if (backend == null)
        {
            return Set.of();
        }
        try
        {
            final Set<String> ids = backend.registryIds();
            return ids != null ? ids : Set.of();
        }
        catch (Throwable failure)
        {
            return Set.of();
        }
    }

    private static RegistryImpl<?> registryLocked(RegistryKey<?> key)
    {
        return registries.computeIfAbsent(key.id(), id -> new RegistryImpl<>(backend, key));
    }

    @SuppressWarnings("unchecked")
    private static <T> void flushOne(DeferredRegister<T> deferred, RegistryView<?> registry,
            Consumer<String> log)
    {
        deferred.flush((RegistryView<T>) registry, log);
    }

    private static void flushWithoutRegistry(DeferredRegister<?> deferred, Consumer<String> log)
    {
        try
        {
            deferred.flush(null, log);
        }
        catch (Throwable failure)
        {
            safeLog(log, "Failed to fail deferred register entries: " + describe(failure));
        }
    }

    private static void safeLog(Consumer<String> log, String message)
    {
        if (log == null)
        {
            return;
        }
        try
        {
            log.accept(message);
        }
        catch (Throwable ignored)
        {
        }
    }

    private static String describe(Throwable failure)
    {
        final String message = failure.getMessage();
        return message != null ? message : failure.getClass().getName();
    }
}
