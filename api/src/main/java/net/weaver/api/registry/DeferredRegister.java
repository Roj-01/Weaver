package net.weaver.api.registry;

import net.weaver.api.ModContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class DeferredRegister<T>
{
    private final RegistryKey<?> registryKey;
    private final String namespace;
    private final List<PendingEntry<T>> pendingEntries = new ArrayList<>();
    private final Set<RegistryKey<T>> keys = new HashSet<>();

    private DeferredRegister(RegistryKey<?> registryKey, String namespace)
    {
        this.registryKey = registryKey;
        this.namespace = namespace;
    }

    public static <T> DeferredRegister<T> create(RegistryKey<?> registry, String namespace)
    {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(namespace, "namespace");
        final DeferredRegister<T> deferred = new DeferredRegister<>(registry, namespace);
        RegistryManager.track(deferred);
        return deferred;
    }

    public static <T> DeferredRegister<T> create(ModContext context, RegistryKey<?> registry)
    {
        Objects.requireNonNull(context, "context");
        return create(registry, context.id());
    }

    public RegistryKey<?> registryKey()
    {
        return registryKey;
    }

    public String namespace()
    {
        return namespace;
    }

    public RegistryEntry<T> register(String path, Supplier<? extends T> supplier)
    {
        Objects.requireNonNull(path, "path");
        final RegistryKey<T> key = path.indexOf(':') >= 0
                ? RegistryKey.parse(path)
                : RegistryKey.of(namespace, path);
        return register(key, supplier);
    }

    public RegistryEntry<T> register(RegistryKey<T> key, Supplier<? extends T> supplier)
    {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(supplier, "supplier");
        // Checked before taking this register's lock: RegistryManager has its own lock.
        final boolean frozen = isTargetFrozen();
        final RegistryEntry<T> entry;
        synchronized (this)
        {
            if (!keys.add(key))
            {
                throw new RegistryException("Duplicate registry key: " + key.id());
            }
            entry = new RegistryEntry<>(key);
            pendingEntries.add(new PendingEntry<>(entry, supplier));
        }
        if (frozen)
        {
            // The final flush has already run, so this entry would otherwise stay
            // unbound forever. Fail it now, as a post-freeze flush would.
            final String message = "Failed to register " + key.id() + ": RegistryView is frozen: "
                    + registryKey.id() + " (registered after the registration window closed)";
            entry.fail(new RegistryException(message));
            RegistryManager.log(message);
        }
        return entry;
    }

    public List<RegistryEntry<T>> entries()
    {
        synchronized (this)
        {
            final List<RegistryEntry<T>> result = new ArrayList<>(pendingEntries.size());
            for (PendingEntry<T> pending : pendingEntries)
            {
                result.add(pending.entry);
            }
            return List.copyOf(result);
        }
    }

    public int size()
    {
        synchronized (this)
        {
            return pendingEntries.size();
        }
    }

    void flush(RegistryView<T> registry, Consumer<String> log)
    {
        final Consumer<String> logger = log != null ? log : message -> {
        };
        if (registry == null)
        {
            failUnbound(new RegistryException("RegistryView is not available: " + registryKey.id()),
                    logger);
            return;
        }
        final boolean frozen;
        try
        {
            frozen = registry.isFrozen();
        }
        catch (Throwable failure)
        {
            failUnbound(new RegistryException("RegistryView is not available: " + registryKey.id(),
                    failure), logger);
            return;
        }
        if (frozen)
        {
            failUnbound(new RegistryException("RegistryView is frozen: " + registryKey.id()), logger);
            return;
        }
        // A supplier may register further entries on this register while it runs;
        // keep flushing until a pass finds nothing left unbound.
        List<PendingEntry<T>> unbound = unbound();
        while (!unbound.isEmpty())
        {
            flushPass(unbound, registry, logger);
            unbound = unbound();
        }
    }

    private void flushPass(List<PendingEntry<T>> unbound, RegistryView<T> registry,
                           Consumer<String> logger)
    {
        for (PendingEntry<T> pending : unbound)
        {
            try
            {
                // Check for a duplicate before running the supplier: constructing a
                // game object (for example a Block or Item) creates an intrusive holder
                // that the game requires to be registered before its registry freezes.
                if (registry.containsKey(pending.entry.key()))
                {
                    throw new RegistryException("Duplicate registry key: " + pending.entry.id());
                }
                final T value = pending.supplier.get();
                if (value == null)
                {
                    throw new RegistryException("Supplier returned null for " + pending.entry.id());
                }
                final RegistryEntry<T> registered = registry.register(pending.entry.key(), value);
                pending.entry.bind(value, registered.holder());
            }
            catch (Throwable failure)
            {
                pending.entry.fail(failure);
                safeLog(logger, "Failed to register " + pending.entry.id() + ": "
                        + describe(failure));
            }
        }
    }

    private List<PendingEntry<T>> snapshot()
    {
        synchronized (this)
        {
            return List.copyOf(pendingEntries);
        }
    }

    private List<PendingEntry<T>> unbound()
    {
        final List<PendingEntry<T>> result = new ArrayList<>();
        for (PendingEntry<T> pending : snapshot())
        {
            if (pending.entry.isUnbound())
            {
                result.add(pending);
            }
        }
        return result;
    }

    private boolean isTargetFrozen()
    {
        try
        {
            return RegistryManager.findRegistry(registryKey)
                    .map(RegistryView::isFrozen)
                    .orElse(false);
        }
        catch (Throwable failure)
        {
            // No backend or an unavailable registry: the flush reports it later.
            return false;
        }
    }

    private void failUnbound(Throwable failure, Consumer<String> log)
    {
        for (PendingEntry<T> pending : snapshot())
        {
            if (pending.entry.isUnbound())
            {
                pending.entry.fail(failure);
            }
        }
        safeLog(log, "Failed to flush deferred register " + registryKey.id() + ": "
                + describe(failure));
    }

    private static void safeLog(Consumer<String> log, String message)
    {
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

    private static final class PendingEntry<T>
    {
        private final RegistryEntry<T> entry;
        private final Supplier<? extends T> supplier;

        private PendingEntry(RegistryEntry<T> entry, Supplier<? extends T> supplier)
        {
            this.entry = entry;
            this.supplier = supplier;
        }
    }
}
