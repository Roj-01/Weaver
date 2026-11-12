package net.weaver.core.registry;

import net.weaver.api.registry.RegistryBackend;
import net.weaver.api.registry.RegistryException;
import net.weaver.core.LifecycleHooks;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reflection-only bridge over the game's built-in registries.
 *
 * <p>Reflection failures are contained: reads return their empty/null
 * fallback, register throws {@link RegistryException}, and each operation is
 * logged at most once per backend instance. {@link #lastFailure()} and
 * {@link #failureCounts()} expose the underlying cause for loader diagnostics
 * and tests without changing the {@link RegistryBackend} contract.</p>
 */
public final class MinecraftRegistryBackend implements RegistryBackend
{
    private static final String BUILT_IN_REGISTRIES = "net.minecraft.core.registries.BuiltInRegistries";
    private static final String BOOTSTRAP = "net.minecraft.server.Bootstrap";
    private static final String REGISTRY = "net.minecraft.core.Registry";
    private static final String RESOURCE_KEY = "net.minecraft.resources.ResourceKey";
    private static final String IDENTIFIER = "net.minecraft.resources.Identifier";
    private static final String TAG_KEY = "net.minecraft.tags.TagKey";
    private static final String HOLDER = "net.minecraft.core.Holder";

    private final ClassLoader gameLoader;
    private final Set<Object> frozen = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>()));
    private final Set<Operation> loggedFailures = ConcurrentHashMap.newKeySet();
    private final Map<Operation, AtomicLong> failureCounts = new ConcurrentHashMap<>();
    private volatile Failure lastFailure;
    private volatile Reflection reflection;
    private volatile BootstrapProbe bootstrapProbe;
    private volatile boolean bootstrapProbeResolved;
    private volatile Map<String, Object> registries = Map.of();

    public MinecraftRegistryBackend(ClassLoader gameLoader)
    {
        this.gameLoader = gameLoader;
    }

    /** A backend operation that can fail independently. */
    public enum Operation
    {
        RESOLVE,
        DISCOVERY,
        GET,
        CONTAINS,
        KEYS,
        ENTRIES,
        HOLDER,
        IDENTIFIER,
        TAG,
        REGISTER
    }

    /**
     * The most recent reflection failure, including how many times its
     * operation has failed. {@code cause} is {@code null} when reflection could
     * not be resolved at all.
     */
    public record Failure(Operation operation, String message, Throwable cause, long count)
    {
    }

    /** The last failure observed, or {@code null} when no operation has failed. */
    public Failure lastFailure()
    {
        return lastFailure;
    }

    /** Failure counts per operation, as an immutable snapshot. */
    public Map<Operation, Long> failureCounts()
    {
        final Map<Operation, Long> snapshot = new EnumMap<>(Operation.class);
        failureCounts.forEach((operation, count) -> snapshot.put(operation, count.get()));
        return Collections.unmodifiableMap(snapshot);
    }

    @Override
    public boolean isAvailable()
    {
        return reflection() != null;
    }

    @Override
    public Set<String> registryIds()
    {
        return registries().keySet();
    }

    @Override
    public Object nativeRegistry(String registryId)
    {
        if (registryId == null)
        {
            return null;
        }
        return registries().get(registryId);
    }

    @Override
    public Object register(Object nativeRegistry, String namespace, String path, Object value)
    {
        Reflection r = reflection();
        if (r == null)
        {
            failUnavailable(Operation.REGISTER);
            throw new RegistryException("Game registry backend is not available");
        }
        if (isFrozen(nativeRegistry))
        {
            throw new RegistryException("Registry is frozen: " + namespace + ":" + path);
        }
        try
        {
            Object identifier = r.fromNamespaceAndPath.invoke(null, namespace, path);
            return r.registerForHolder.invoke(null, nativeRegistry, identifier, value);
        }
        catch (Throwable failure)
        {
            fail(Operation.REGISTER, failure);
            throw new RegistryException("Register failed: " + namespace + ":" + path,
                    unwrap(failure));
        }
    }

    @Override
    public Object get(Object nativeRegistry, String namespace, String path)
    {
        Reflection r = reflection();
        if (r == null)
        {
            failUnavailable(Operation.GET);
            return null;
        }
        if (nativeRegistry == null)
        {
            return null;
        }
        try
        {
            Object identifier = r.fromNamespaceAndPath.invoke(null, namespace, path);
            return r.getValue.invoke(nativeRegistry, identifier);
        }
        catch (Throwable failure)
        {
            fail(Operation.GET, failure);
            return null;
        }
    }

    @Override
    public boolean containsKey(Object nativeRegistry, String namespace, String path)
    {
        Reflection r = reflection();
        if (r == null)
        {
            failUnavailable(Operation.CONTAINS);
            return false;
        }
        if (nativeRegistry == null)
        {
            return false;
        }
        try
        {
            Object identifier = r.fromNamespaceAndPath.invoke(null, namespace, path);
            return Boolean.TRUE.equals(r.containsKey.invoke(nativeRegistry, identifier));
        }
        catch (Throwable failure)
        {
            fail(Operation.CONTAINS, failure);
            return false;
        }
    }

    @Override
    public List<String> keys(Object nativeRegistry)
    {
        Reflection r = reflection();
        if (r == null)
        {
            failUnavailable(Operation.KEYS);
            return List.of();
        }
        if (nativeRegistry == null)
        {
            return List.of();
        }
        try
        {
            Object result = r.keySet.invoke(nativeRegistry);
            if (!(result instanceof Set<?> identifiers))
            {
                fail(Operation.KEYS, new RegistryException(
                        "Registry.keySet() returned " + typeName(result)));
                return List.of();
            }
            List<String> keys = new ArrayList<>(identifiers.size());
            for (Object identifier : identifiers)
            {
                if (identifier == null)
                {
                    fail(Operation.KEYS, new RegistryException(
                            "Registry.keySet() contained a null identifier"));
                    continue;
                }
                try
                {
                    keys.add(identifier.toString());
                }
                catch (Throwable failure)
                {
                    fail(Operation.KEYS, failure);
                }
            }
            return List.copyOf(keys);
        }
        catch (Throwable failure)
        {
            fail(Operation.KEYS, failure);
            return List.of();
        }
    }

    @Override
    public List<Map.Entry<String, Object>> entries(Object nativeRegistry)
    {
        Reflection r = reflection();
        if (r == null)
        {
            failUnavailable(Operation.ENTRIES);
            return List.of();
        }
        if (nativeRegistry == null)
        {
            return List.of();
        }
        try
        {
            Object result = r.entrySet.invoke(nativeRegistry);
            if (!(result instanceof Set<?> nativeEntries))
            {
                fail(Operation.ENTRIES, new RegistryException(
                        "Registry.entrySet() returned " + typeName(result)));
                return List.of();
            }
            List<Map.Entry<String, Object>> entries = new ArrayList<>(nativeEntries.size());
            for (Object item : nativeEntries)
            {
                if (!(item instanceof Map.Entry<?, ?> entry) || entry.getKey() == null)
                {
                    fail(Operation.ENTRIES, new RegistryException(
                            "Registry.entrySet() contained an invalid entry"));
                    continue;
                }
                final Object identifier;
                try
                {
                    identifier = r.resourceKeyIdentifier.invoke(entry.getKey());
                }
                catch (Throwable failure)
                {
                    fail(Operation.ENTRIES, failure);
                    continue;
                }
                if (identifier == null)
                {
                    fail(Operation.ENTRIES, new RegistryException(
                            "ResourceKey.identifier() returned null"));
                    continue;
                }
                final Object value = entry.getValue();
                if (value == null)
                {
                    fail(Operation.ENTRIES, new RegistryException(
                            "Registry entry value is null for " + identifier));
                    continue;
                }
                entries.add(new AbstractMap.SimpleImmutableEntry<>(identifier.toString(), value));
            }
            return List.copyOf(entries);
        }
        catch (Throwable failure)
        {
            fail(Operation.ENTRIES, failure);
            return List.of();
        }
    }

    @Override
    public Object holder(Object nativeRegistry, Object value)
    {
        Reflection r = reflection();
        if (r == null)
        {
            failUnavailable(Operation.HOLDER);
            return null;
        }
        if (nativeRegistry == null)
        {
            return null;
        }
        try
        {
            return r.wrapAsHolder.invoke(nativeRegistry, value);
        }
        catch (Throwable failure)
        {
            fail(Operation.HOLDER, failure);
            return null;
        }
    }

    @Override
    public List<Object> tagValues(Object nativeRegistry, String registryId,
                                  String tagNamespace, String tagPath)
    {
        Reflection r = reflection();
        if (r == null)
        {
            failUnavailable(Operation.TAG);
            return List.of();
        }
        if (nativeRegistry == null || registryId == null)
        {
            return List.of();
        }
        try
        {
            int separator = registryId.indexOf(':');
            String namespace = separator < 0 ? "minecraft" : registryId.substring(0, separator);
            String path = separator < 0 ? registryId : registryId.substring(separator + 1);
            Object registryIdentifier = r.fromNamespaceAndPath.invoke(null, namespace, path);
            Object registryKey = r.createRegistryKey.invoke(null, registryIdentifier);
            Object tagIdentifier = r.fromNamespaceAndPath.invoke(null, tagNamespace, tagPath);
            Object tagKey = r.tagCreate.invoke(null, registryKey, tagIdentifier);
            Object result = r.getTagOrEmpty.invoke(nativeRegistry, tagKey);
            if (!(result instanceof Iterable<?> holders))
            {
                fail(Operation.TAG, new RegistryException(
                        "Registry.getTagOrEmpty() returned " + typeName(result)));
                return List.of();
            }
            List<Object> values = new ArrayList<>();
            for (Object holder : holders)
            {
                if (holder == null)
                {
                    continue;
                }
                final Object value;
                try
                {
                    value = r.holderValue.invoke(holder);
                }
                catch (Throwable failure)
                {
                    fail(Operation.TAG, failure);
                    continue;
                }
                if (value == null)
                {
                    fail(Operation.TAG, new RegistryException(
                            "Holder.value() returned null for tag " + tagNamespace + ":" + tagPath));
                    continue;
                }
                values.add(value);
            }
            return List.copyOf(values);
        }
        catch (Throwable failure)
        {
            fail(Operation.TAG, failure);
            return List.of();
        }
    }

    @Override
    public Object identifier(String namespace, String path)
    {
        Reflection r = reflection();
        if (r == null)
        {
            failUnavailable(Operation.IDENTIFIER);
            return null;
        }
        try
        {
            return r.fromNamespaceAndPath.invoke(null, namespace, path);
        }
        catch (Throwable failure)
        {
            fail(Operation.IDENTIFIER, failure);
            return null;
        }
    }

    @Override
    public void markFrozen(Object nativeRegistry)
    {
        if (nativeRegistry != null)
        {
            frozen.add(nativeRegistry);
        }
    }

    @Override
    public boolean isFrozen(Object nativeRegistry)
    {
        return nativeRegistry != null && frozen.contains(nativeRegistry);
    }

    private Map<String, Object> registries()
    {
        Map<String, Object> current = registries;
        if (!current.isEmpty())
        {
            return current;
        }
        synchronized (this)
        {
            if (!registries.isEmpty())
            {
                return registries;
            }
            Reflection r = reflection();
            if (r == null)
            {
                return Map.of();
            }
            if (!bootstrapped())
            {
                return Map.of();
            }
            try
            {
                Class<?> builtIns = Class.forName(BUILT_IN_REGISTRIES, false, loader());
                Map<String, Object> found = new LinkedHashMap<>();
                for (Field field : builtIns.getFields())
                {
                    int modifiers = field.getModifiers();
                    if (!Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers))
                    {
                        continue;
                    }
                    if (!r.registryType.isAssignableFrom(field.getType()))
                    {
                        continue;
                    }
                    Object registry;
                    try
                    {
                        registry = field.get(null);
                    }
                    catch (Throwable failure)
                    {
                        fail(Operation.DISCOVERY, failure);
                        continue;
                    }
                    if (registry == null)
                    {
                        continue;
                    }
                    String id = registryId(r, registry);
                    if (id != null)
                    {
                        found.putIfAbsent(id, registry);
                    }
                }
                if (!found.isEmpty())
                {
                    registries = Collections.unmodifiableMap(found);
                }
                return registries;
            }
            catch (Throwable failure)
            {
                fail(Operation.DISCOVERY, failure);
                return Map.of();
            }
        }
    }

    private String registryId(Reflection r, Object registry)
    {
        try
        {
            Object key = r.registryKey.invoke(registry);
            if (key == null)
            {
                return null;
            }
            Object identifier = r.resourceKeyIdentifier.invoke(key);
            return identifier == null ? null : identifier.toString();
        }
        catch (Throwable failure)
        {
            fail(Operation.DISCOVERY, failure);
            return null;
        }
    }

    private boolean bootstrapped()
    {
        BootstrapProbe probe = bootstrapProbe;
        if (probe == null && !bootstrapProbeResolved)
        {
            synchronized (this)
            {
                if (!bootstrapProbeResolved)
                {
                    bootstrapProbe = BootstrapProbe.resolve(loader());
                    bootstrapProbeResolved = true;
                }
                probe = bootstrapProbe;
            }
        }
        return probe == null || probe.isBootstrapped();
    }

    private Reflection reflection()
    {
        Reflection current = reflection;
        if (current != null)
        {
            return current;
        }
        synchronized (this)
        {
            if (reflection == null)
            {
                reflection = resolve();
            }
            return reflection;
        }
    }

    private Reflection resolve()
    {
        try
        {
            ClassLoader loader = loader();
            Class<?> registry = Class.forName(REGISTRY, false, loader);
            Class<?> identifier = Class.forName(IDENTIFIER, false, loader);
            Class<?> resourceKey = Class.forName(RESOURCE_KEY, false, loader);
            Class<?> tagKey = Class.forName(TAG_KEY, false, loader);
            Class<?> holder = Class.forName(HOLDER, false, loader);
            Method registryKey = registry.getMethod("key");
            Method resourceKeyIdentifier = resourceKey.getMethod("identifier");
            Method createRegistryKey = resourceKey.getMethod("createRegistryKey", identifier);
            Method fromNamespaceAndPath = identifier.getMethod("fromNamespaceAndPath", String.class,
                    String.class);
            Method registerForHolder = registry.getMethod("registerForHolder", registry, identifier,
                    Object.class);
            Method getValue = registry.getMethod("getValue", identifier);
            Method containsKey = registry.getMethod("containsKey", identifier);
            Method keySet = registry.getMethod("keySet");
            Method entrySet = registry.getMethod("entrySet");
            Method wrapAsHolder = registry.getMethod("wrapAsHolder", Object.class);
            Method tagCreate = tagKey.getMethod("create", resourceKey, identifier);
            Method getTagOrEmpty = registry.getMethod("getTagOrEmpty", tagKey);
            Method holderValue = holder.getMethod("value");
            return new Reflection(registry, registryKey, resourceKeyIdentifier, createRegistryKey,
                    fromNamespaceAndPath, registerForHolder, getValue, containsKey, keySet, entrySet,
                    wrapAsHolder, tagCreate, getTagOrEmpty, holderValue);
        }
        catch (Throwable failure)
        {
            fail(Operation.RESOLVE, failure);
            return null;
        }
    }

    private ClassLoader loader()
    {
        return gameLoader != null ? gameLoader : ClassLoader.getSystemClassLoader();
    }

    private void failUnavailable(Operation operation)
    {
        fail(operation, null);
    }

    private void fail(Operation operation, Throwable failure)
    {
        final AtomicLong counter = failureCounts.computeIfAbsent(operation,
                ignored -> new AtomicLong());
        final long count = counter.incrementAndGet();
        final String message = operation.name() + " failed: " + describe(failure);
        final Throwable cause = failure == null ? null : unwrap(failure);
        lastFailure = new Failure(operation, message, cause, count);
        if (loggedFailures.add(operation))
        {
            try
            {
                LifecycleHooks.logLine("[weaver] REGISTRY BACKEND " + operation.name()
                        + " FAILED reason=" + describe(failure));
            }
            catch (Throwable ignored)
            {
            }
        }
    }

    private static String typeName(Object value)
    {
        return value == null ? "null" : value.getClass().getName();
    }

    private static Throwable unwrap(Throwable failure)
    {
        if (failure instanceof InvocationTargetException invocation && invocation.getCause() != null)
        {
            return invocation.getCause();
        }
        return failure;
    }

    private static String describe(Throwable failure)
    {
        if (failure == null)
        {
            return "backend is not available";
        }
        final Throwable cause = unwrap(failure);
        final String message = cause.getMessage();
        return message != null ? message : cause.getClass().getName();
    }

    private record Reflection(Class<?> registryType, Method registryKey, Method resourceKeyIdentifier,
                              Method createRegistryKey, Method fromNamespaceAndPath,
                              Method registerForHolder, Method getValue, Method containsKey,
                              Method keySet, Method entrySet, Method wrapAsHolder, Method tagCreate,
                              Method getTagOrEmpty, Method holderValue)
    {
    }

    private static final class BootstrapProbe
    {
        private static final BootstrapProbe MISSING = new BootstrapProbe(null);

        private final Field bootstrapped;

        private BootstrapProbe(Field bootstrapped)
        {
            this.bootstrapped = bootstrapped;
        }

        private static BootstrapProbe resolve(ClassLoader loader)
        {
            try
            {
                Class<?> bootstrap = Class.forName(BOOTSTRAP, false, loader);
                Field field = bootstrap.getDeclaredField("isBootstrapped");
                field.setAccessible(true);
                return new BootstrapProbe(field);
            }
            catch (ClassNotFoundException missing)
            {
                return MISSING;
            }
            catch (Throwable failure)
            {
                return new BootstrapProbe(null);
            }
        }

        private boolean isBootstrapped()
        {
            if (this == MISSING)
            {
                return true;
            }
            if (bootstrapped == null)
            {
                return false;
            }
            try
            {
                return bootstrapped.getBoolean(null);
            }
            catch (Throwable failure)
            {
                return false;
            }
        }
    }
}
