package net.weaver.api.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class RegistryImpl<T> implements RegistryView<T>
{
    private final RegistryBackend backend;
    private final RegistryKey<?> registryKey;
    private final Object nativeLock = new Object();
    private final Object cacheLock = new Object();
    private final Set<String> loggedReadFailures = ConcurrentHashMap.newKeySet();
    private volatile Object nativeRegistry;
    private volatile Set<RegistryKey<T>> keyCache;
    private volatile Collection<T> valueCache;
    private volatile List<RegistryEntry<T>> entriesCache;
    private volatile Map<String, RegistryEntry<T>> entryCache;

    RegistryImpl(RegistryBackend backend, RegistryKey<?> registryKey)
    {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.registryKey = Objects.requireNonNull(registryKey, "registryKey");
    }

    @Override
    public RegistryKey<?> registryKey()
    {
        return registryKey;
    }

    @Override
    public String id()
    {
        return registryKey.id();
    }

    @Override
    public Object nativeRegistry()
    {
        Object resolved = nativeRegistry;
        if (resolved == null)
        {
            synchronized (nativeLock)
            {
                resolved = nativeRegistry;
                if (resolved == null)
                {
                    resolved = backend.nativeRegistry(id());
                    if (resolved == null)
                    {
                        throw new RegistryException("RegistryView is not available: " + id());
                    }
                    nativeRegistry = resolved;
                }
            }
        }
        return resolved;
    }

    @Override
    public boolean isFrozen()
    {
        return backend.isFrozen(nativeRegistry());
    }

    @Override
    public boolean containsKey(RegistryKey<T> key)
    {
        Objects.requireNonNull(key, "key");
        final Object nativeHandle = nativeRegistryOrNull("CONTAINS");
        if (nativeHandle == null)
        {
            return false;
        }
        try
        {
            return backend.containsKey(nativeHandle, key.namespace(), key.path());
        }
        catch (Throwable failure)
        {
            readFailure("CONTAINS", failure);
            return false;
        }
    }

    @Override
    public boolean containsKey(String id)
    {
        Objects.requireNonNull(id, "id");
        return containsKey(RegistryKey.parse(id));
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get(RegistryKey<T> key)
    {
        Objects.requireNonNull(key, "key");
        final Object nativeHandle = nativeRegistryOrNull("GET");
        if (nativeHandle == null)
        {
            return null;
        }
        try
        {
            return (T) backend.get(nativeHandle, key.namespace(), key.path());
        }
        catch (Throwable failure)
        {
            readFailure("GET", failure);
            return null;
        }
    }

    @Override
    public T get(String id)
    {
        Objects.requireNonNull(id, "id");
        return get(RegistryKey.parse(id));
    }

    @Override
    public Optional<T> getOptional(RegistryKey<T> key)
    {
        return Optional.ofNullable(get(key));
    }

    @Override
    public Optional<RegistryEntry<T>> getEntry(RegistryKey<T> key)
    {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(entryMap().get(key.id()));
    }

    @Override
    public Optional<RegistryEntry<T>> getEntry(String id)
    {
        Objects.requireNonNull(id, "id");
        return getEntry(RegistryKey.parse(id));
    }

    @Override
    public Set<RegistryKey<T>> keySet()
    {
        Set<RegistryKey<T>> cached = keyCache;
        if (cached == null)
        {
            synchronized (cacheLock)
            {
                cached = keyCache;
                if (cached == null)
                {
                    final Object nativeHandle = nativeRegistryOrNull("KEYS");
                    if (nativeHandle == null)
                    {
                        return Collections.emptySet();
                    }
                    final List<String> nativeKeys;
                    try
                    {
                        nativeKeys = backend.keys(nativeHandle);
                    }
                    catch (Throwable failure)
                    {
                        readFailure("KEYS", failure);
                        return Collections.emptySet();
                    }
                    final Set<RegistryKey<T>> built = new LinkedHashSet<>();
                    if (nativeKeys != null)
                    {
                        for (String entryId : nativeKeys)
                        {
                            if (entryId == null)
                            {
                                readFailure("KEYS", new RegistryException(
                                        "Backend returned a null registry key"));
                                continue;
                            }
                            try
                            {
                                built.add(RegistryKey.parse(entryId));
                            }
                            catch (Throwable failure)
                            {
                                readFailure("KEYS", failure);
                            }
                        }
                    }
                    cached = Collections.unmodifiableSet(built);
                    keyCache = cached;
                }
            }
        }
        return cached;
    }

    @Override
    public Collection<T> values()
    {
        Collection<T> cached = valueCache;
        if (cached == null)
        {
            synchronized (cacheLock)
            {
                cached = valueCache;
                if (cached == null)
                {
                    final Object nativeHandle = nativeRegistryOrNull("VALUES");
                    if (nativeHandle == null)
                    {
                        return Collections.emptyList();
                    }
                    final List<Map.Entry<String, Object>> nativeEntries;
                    try
                    {
                        nativeEntries = backend.entries(nativeHandle);
                    }
                    catch (Throwable failure)
                    {
                        readFailure("VALUES", failure);
                        return Collections.emptyList();
                    }
                    final List<T> built = new ArrayList<>();
                    if (nativeEntries != null)
                    {
                        for (Map.Entry<String, Object> nativeEntry : nativeEntries)
                        {
                            if (nativeEntry == null || nativeEntry.getValue() == null)
                            {
                                readFailure("VALUES", new RegistryException(
                                        "Backend returned an invalid registry entry"));
                                continue;
                            }
                            built.add(cast(nativeEntry.getValue()));
                        }
                    }
                    cached = Collections.unmodifiableList(built);
                    valueCache = cached;
                }
            }
        }
        return cached;
    }

    @Override
    public List<RegistryEntry<T>> entries()
    {
        synchronized (cacheLock)
        {
            if (entriesCache == null)
            {
                buildEntryCachesLocked("ENTRIES");
            }
            return entriesCache != null ? entriesCache : Collections.emptyList();
        }
    }

    @Override
    public int size()
    {
        return keySet().size();
    }

    @Override
    public RegistryEntry<T> register(RegistryKey<T> key, T value)
    {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        final Object nativeHandle = nativeRegistry();
        if (backend.isFrozen(nativeHandle))
        {
            throw new RegistryException("RegistryView is frozen: " + id());
        }
        final Object holder;
        try
        {
            holder = backend.register(nativeHandle, key.namespace(), key.path(), value);
        }
        finally
        {
            invalidateCaches();
        }
        final RegistryEntry<T> entry = new RegistryEntry<>(key);
        entry.bind(value, holder);
        return entry;
    }

    @Override
    public RegistryEntry<T> register(String id, T value)
    {
        Objects.requireNonNull(id, "id");
        return register(RegistryKey.parse(id), value);
    }

    @Override
    public TagView<T> getTag(TagKey<T> tag)
    {
        Objects.requireNonNull(tag, "tag");
        if (!registryKey.equals(tag.registry()))
        {
            throw new RegistryException("Tag " + tag + " belongs to registry "
                    + tag.registry().id() + ", not " + registryKey.id());
        }
        final Object nativeHandle = nativeRegistryOrNull("TAG");
        if (nativeHandle == null)
        {
            return new TagView<>(tag, List.of());
        }
        final List<Object> values;
        try
        {
            final List<Object> resolved = backend.tagValues(nativeHandle, registryKey.id(),
                    tag.namespace(), tag.path());
            values = resolved != null ? resolved : List.of();
        }
        catch (Throwable failure)
        {
            readFailure("TAG", failure);
            return new TagView<>(tag, List.of());
        }
        final List<T> cast = new ArrayList<>(values.size());
        for (Object value : values)
        {
            if (value == null)
            {
                readFailure("TAG", new RegistryException(
                        "Backend returned a null tag value for " + tag.id()));
                continue;
            }
            cast.add(cast(value));
        }
        return new TagView<>(tag, cast);
    }

    void freeze()
    {
        try
        {
            backend.markFrozen(nativeRegistry());
        }
        finally
        {
            invalidateCaches();
        }
    }

    void invalidateCaches()
    {
        synchronized (cacheLock)
        {
            keyCache = null;
            valueCache = null;
            entriesCache = null;
            entryCache = null;
        }
    }


    private Map<String, RegistryEntry<T>> entryMap()
    {
        synchronized (cacheLock)
        {
            if (entryCache == null)
            {
                buildEntryCachesLocked("ENTRY");
            }
            return entryCache != null ? entryCache : Collections.emptyMap();
        }
    }

    private void buildEntryCachesLocked(String operation)
    {
        final Object nativeHandle = nativeRegistryOrNull(operation);
        if (nativeHandle == null)
        {
            return;
        }
        final List<Map.Entry<String, Object>> nativeEntries;
        try
        {
            nativeEntries = backend.entries(nativeHandle);
        }
        catch (Throwable failure)
        {
            readFailure(operation, failure);
            return;
        }
        if (nativeEntries == null)
        {
            return;
        }
        final List<RegistryEntry<T>> builtEntries = new ArrayList<>(nativeEntries.size());
        final Map<String, RegistryEntry<T>> builtMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> nativeEntry : nativeEntries)
        {
            if (nativeEntry == null || nativeEntry.getKey() == null
                    || nativeEntry.getValue() == null)
            {
                readFailure(operation, new RegistryException(
                        "Backend returned an invalid registry entry"));
                continue;
            }
            final RegistryKey<T> key;
            try
            {
                key = RegistryKey.parse(nativeEntry.getKey());
            }
            catch (Throwable failure)
            {
                readFailure(operation, failure);
                continue;
            }
            final Object value = nativeEntry.getValue();
            Object holder = null;
            try
            {
                holder = backend.holder(nativeHandle, value);
            }
            catch (Throwable failure)
            {
                readFailure("HOLDER", failure);
            }
            final RegistryEntry<T> entry = new RegistryEntry<>(key);
            entry.bind(value, holder);
            builtEntries.add(entry);
            builtMap.put(key.id(), entry);
        }
        entriesCache = Collections.unmodifiableList(builtEntries);
        entryCache = Collections.unmodifiableMap(builtMap);
    }

    private Object nativeRegistryOrNull(String operation)
    {
        try
        {
            return nativeRegistry();
        }
        catch (Throwable failure)
        {
            readFailure(operation, failure);
            return null;
        }
    }

    private void readFailure(String operation, Throwable failure)
    {
        if (loggedReadFailures.add(operation))
        {
            RegistryManager.log("[weaver] REGISTRY READ " + operation + " FAILED registry="
                    + id() + " reason=" + describe(failure));
        }
    }

    @SuppressWarnings("unchecked")
    private static <V> V cast(Object value)
    {
        return (V) value;
    }

    private static String describe(Throwable failure)
    {
        final String message = failure.getMessage();
        return message != null ? message : failure.getClass().getName();
    }
}
