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

/**
 * A typed, data-driven registry with priority-aware pack loading and tags.
 *
 * <p>Programmatic entries are registered with {@link #register(DataRegistryKey,
 * Object)} or {@link #register(String, Object)} and receive
 * {@link Integer#MAX_VALUE} priority, so a data pack cannot replace them.
 * Pack entries are loaded through {@link #load(String, Map, int, String)} and
 * are compared by priority: a higher-priority value wins, an equal-priority
 * value is a duplicate, and a lower-priority value is skipped.</p>
 *
 * <p>Reads are synchronized and return immutable snapshots, except
 * {@link RegistryEntry} handles, which remain live. {@link #freeze()} is
 * permanent and also freezes the tag store. This class is safe for concurrent
 * reads; write operations are serialized on the registry instance.</p>
 *
 * @param <T> the entry type
 */
public final class DataRegistry<T>
{
    /**
     * The outcome of loading one entry or one tag value.
     */
    public enum Status
    {
        /** A new entry was accepted. */
        ADDED,
        /** An existing lower-priority entry was replaced. */
        REPLACED,
        /** An existing higher-priority entry was left alone. */
        SKIPPED,
        /** An existing entry at the same priority was left alone. */
        DUPLICATE,
        /** The codec threw or returned {@code null}; an old value was kept. */
        DECODE_FAILED,
        /** The registry is frozen and the write was rejected. */
        FROZEN,
        /** The entry path or tag value was invalid. */
        INVALID,
        /** An optional tag value is not present in the registry. */
        MISSING,
        /** A required tag value is not present in the registry. */
        REQUIRED_MISSING
    }

    /**
     * The result of one load operation.
     *
     * @param status the outcome, not {@code null}
     * @param entryId the affected entry or tag id, not {@code null}
     * @param detail a human-readable explanation, never {@code null}
     */
    public record LoadResult(Status status, String entryId, String detail)
    {
        /** Validates the status and entry id and normalizes a null detail. */
        public LoadResult
        {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(entryId, "entryId");
            detail = detail != null ? detail : "";
        }
    }

    private final RegistryKey<?> registryKey;
    private final Class<T> type;
    private final DataRegistryCodec<T> codec;
    private final TagStore<T> tagStore;
    private final Map<String, RegistryEntry<T>> entries = new LinkedHashMap<>();
    private final Map<String, Integer> priorities = new LinkedHashMap<>();
    private final Map<String, String> sources = new LinkedHashMap<>();
    private volatile boolean frozen;

    DataRegistry(RegistryKey<?> registryKey, Class<T> type, DataRegistryCodec<T> codec)
    {
        this.registryKey = Objects.requireNonNull(registryKey, "registryKey");
        this.type = Objects.requireNonNull(type, "type");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.tagStore = new TagStore<>(registryKey);
    }

    static <T> DataRegistry<T> create(RegistryKey<?> registryKey, Class<T> type,
                                      DataRegistryCodec<T> codec)
    {
        return new DataRegistry<>(registryKey, type, codec);
    }

    /**
     * The registry key.
     *
     * @return the registry key, never {@code null}
     */
    public RegistryKey<?> registryKey()
    {
        return registryKey;
    }

    /**
     * The registry id.
     *
     * @return the full {@code namespace:path} registry id
     */
    public String id()
    {
        return registryKey.id();
    }

    /**
     * The registry namespace. Pack entry ids and path-based programmatic
     * registrations use this namespace.
     *
     * @return the registry namespace
     */
    public String namespace()
    {
        return registryKey.namespace();
    }

    /**
     * The entry type.
     *
     * @return the entry type, never {@code null}
     */
    public Class<T> type()
    {
        return type;
    }

    /**
     * The codec used by pack loads.
     *
     * @return the codec, never {@code null}
     */
    public DataRegistryCodec<T> codec()
    {
        return codec;
    }

    /**
     * Whether this registry rejects writes.
     *
     * @return {@code true} after a successful {@link #freeze()}
     */
    public boolean isFrozen()
    {
        return frozen;
    }

    /**
     * Registers a programmatic entry.
     *
     * <p>Programmatic entries use {@link Integer#MAX_VALUE} priority and the
     * source {@code api}. They cannot be replaced by a pack load. Registering
     * an id that already exists throws {@link RegistryException}.</p>
     *
     * @param key the entry key, not {@code null} and in this registry
     * @param value the entry value, not {@code null}
     * @return the bound entry
     * @throws IllegalArgumentException when the key belongs to another registry
     *                                  or names another type
     * @throws RegistryException when the registry is frozen or the id already
     *                           exists
     */
    public synchronized RegistryEntry<T> register(DataRegistryKey<T> key, T value)
    {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        ensureWritable();
        validateKey(key);
        final String id = key.id().id();
        if (entries.containsKey(id))
        {
            throw new RegistryException("Duplicate data registry entry: " + id);
        }
        final RegistryEntry<T> entry = new RegistryEntry<>(key.id());
        entry.bind(value, null);
        entries.put(id, entry);
        priorities.put(id, Integer.MAX_VALUE);
        sources.put(id, "api");
        return entry;
    }

    /**
     * Registers a programmatic entry by path.
     *
     * <p>A path without {@code :} is resolved against {@link #namespace()}. A
     * full {@code namespace:path} id is also accepted.</p>
     *
     * @param path the entry path or full id, not {@code null}
     * @param value the entry value, not {@code null}
     * @return the bound entry
     * @throws RegistryException when the registry is frozen or the id already
     *                           exists
     */
    public RegistryEntry<T> register(String path, T value)
    {
        Objects.requireNonNull(path, "path");
        final DataRegistryKey<T> key = path.indexOf(':') >= 0
                ? DataRegistryKey.of(registryKey, path, type)
                : DataRegistryKey.of(registryKey, namespace(), path, type);
        return register(key, value);
    }

    /**
     * Loads one entry from a parsed JSON object.
     *
     * <p>The entry id is {@code registryNamespace + ":" + entryPath}. The codec
     * decodes the data and the entry is compared with any existing entry:
     * higher existing priority means {@link Status#SKIPPED}, equal priority
     * means {@link Status#DUPLICATE}, and lower existing priority means
     * {@link Status#REPLACED}. A replacement keeps the original insertion
     * position. A decode failure keeps the old value.</p>
     *
     * @param entryPath the entry path inside this registry, not {@code null}
     * @param data the parsed JSON object, not {@code null}
     * @param priority the layer priority
     * @param source the diagnostic source label, not {@code null}
     * @return the load result, never {@code null}
     */
    public synchronized LoadResult load(String entryPath, Map<String, Object> data, int priority,
                                        String source)
    {
        Objects.requireNonNull(entryPath, "entryPath");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(source, "source");
        final String entryId = namespace() + ":" + entryPath;
        if (frozen)
        {
            return new LoadResult(Status.FROZEN, entryId,
                    "data registry " + id() + " is frozen");
        }
        final RegistryKey<T> key;
        try
        {
            key = RegistryKey.of(namespace(), entryPath);
        }
        catch (IllegalArgumentException invalid)
        {
            return new LoadResult(Status.INVALID, entryId, describe(invalid));
        }
        final RegistryEntry<T> existing = entries.get(entryId);
        if (existing != null)
        {
            final int existingPriority = priorities.getOrDefault(entryId, Integer.MIN_VALUE);
            if (existingPriority > priority)
            {
                return new LoadResult(Status.SKIPPED, entryId,
                        "existing priority " + existingPriority + " from "
                                + sources.getOrDefault(entryId, "unknown") + " is higher");
            }
            if (existingPriority == priority)
            {
                return new LoadResult(Status.DUPLICATE, entryId,
                        "existing priority " + existingPriority + " from "
                                + sources.getOrDefault(entryId, "unknown") + " is equal");
            }
        }
        final T decoded;
        try
        {
            decoded = codec.decode(entryId, data);
        }
        catch (Throwable failure)
        {
            return new LoadResult(Status.DECODE_FAILED, entryId, describe(failure));
        }
        if (decoded == null)
        {
            return new LoadResult(Status.DECODE_FAILED, entryId, "codec returned null");
        }
        if (existing != null)
        {
            existing.bind(decoded, null);
            priorities.put(entryId, priority);
            sources.put(entryId, source);
            return new LoadResult(Status.REPLACED, entryId,
                    "replaced priority " + priority + " from " + source);
        }
        final RegistryEntry<T> entry = new RegistryEntry<>(key);
        entry.bind(decoded, null);
        entries.put(entryId, entry);
        priorities.put(entryId, priority);
        sources.put(entryId, source);
        return new LoadResult(Status.ADDED, entryId,
                "added priority " + priority + " from " + source);
    }

    /**
     * Loads one tag declaration.
     *
     * <p>Value ids should be full {@code namespace:path} ids; a path without a
     * namespace defaults to {@code minecraft}, matching
     * {@link RegistryKey#parse(String)}. An id starting with {@code #}, or a
     * {@link DataPackSnapshot.TagValue} with {@code tagReference} set, becomes
     * a tag reference. Present values are looked up in this registry. A missing
     * optional value produces {@link Status#MISSING}; a missing required value
     * produces {@link Status#REQUIRED_MISSING}. Both are skipped without
     * aborting the rest of the declaration.</p>
     *
     * <p>One result is returned per declared value. A frozen registry returns a
     * single {@link Status#FROZEN} result and does not modify the tag store.</p>
     *
     * @param tagPath the tag path or full tag id, not {@code null}
     * @param values the declared values, not {@code null}
     * @param priority the layer priority
     * @param source the diagnostic source label, not {@code null}
     * @param replace whether the layer replaces lower-priority layers
     * @return the per-value results, never {@code null}
     */
    public synchronized List<LoadResult> loadTag(String tagPath,
                                                 List<DataPackSnapshot.TagValue> values,
                                                 int priority, String source, boolean replace)
    {
        Objects.requireNonNull(tagPath, "tagPath");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(source, "source");
        final TagKey<T> tagKey;
        try
        {
            tagKey = TagKey.of(registryKey, tagPath);
        }
        catch (IllegalArgumentException invalid)
        {
            return List.of(new LoadResult(Status.INVALID, tagPath, describe(invalid)));
        }
        if (frozen)
        {
            return List.of(new LoadResult(Status.FROZEN, tagKey.id().id(),
                    "data registry " + id() + " is frozen"));
        }
        final List<LoadResult> results = new ArrayList<>(values.size());
        final List<java.util.function.Supplier<? extends T>> directValues = new ArrayList<>();
        final List<TagKey<T>> references = new ArrayList<>();
        for (DataPackSnapshot.TagValue value : values)
        {
            if (value == null)
            {
                results.add(new LoadResult(Status.INVALID, tagKey.id().id(),
                        "null tag value"));
                continue;
            }
            final String rawId = value.id();
            final boolean referenceValue = value.tagReference() || rawId.startsWith("#");
            final String id = referenceValue && rawId.startsWith("#")
                    ? rawId.substring(1)
                    : rawId;
            final RegistryKey<T> valueKey;
            try
            {
                valueKey = RegistryKey.parse(id);
            }
            catch (IllegalArgumentException invalid)
            {
                results.add(new LoadResult(Status.INVALID, rawId, describe(invalid)));
                continue;
            }
            if (referenceValue)
            {
                references.add(TagKey.of(registryKey, valueKey.id()));
                results.add(new LoadResult(Status.ADDED, valueKey.id(),
                        "tag reference from " + source));
                continue;
            }
            final RegistryEntry<T> entry = entries.get(valueKey.id());
            final T resolved = entry != null ? entry.getOrNull() : null;
            if (resolved == null)
            {
                results.add(new LoadResult(value.required()
                        ? Status.REQUIRED_MISSING
                        : Status.MISSING, valueKey.id(),
                        value.required() ? "required tag value is missing"
                                : "optional tag value is missing"));
                continue;
            }
            directValues.add(entry::getOrNull);
            results.add(new LoadResult(Status.ADDED, valueKey.id(),
                    "tag value from " + source));
        }
        try
        {
            tagStore.addPackLayer(tagKey, directValues, references, priority, source, replace);
        }
        catch (RegistryException frozenStore)
        {
            return List.of(new LoadResult(Status.FROZEN, tagKey.id().id(),
                    describe(frozenStore)));
        }
        return List.copyOf(results);
    }

    /**
     * The number of loaded entries.
     *
     * @return the entry count
     */
    public synchronized int size()
    {
        return entries.size();
    }

    /**
     * Prepares an open registry for a reload: drops every pack-loaded tag layer
     * and forgets the priority of pack-loaded entries, so re-applying the same
     * packs replaces values in their existing handles instead of being rejected
     * as equal-priority duplicates. Programmatic ({@code api}) entries and tags
     * added through {@link #addTag} are kept.
     */
    synchronized void resetPackStateForReload()
    {
        if (isFrozen())
        {
            return;
        }
        for (Map.Entry<String, String> source : sources.entrySet())
        {
            if (!"api".equals(source.getValue()))
            {
                priorities.remove(source.getKey());
            }
        }
        tagStore.removePackLayers();
    }

    /**
     * A snapshot of the entry keys.
     *
     * @return an unmodifiable set in insertion order
     */
    public synchronized Set<DataRegistryKey<T>> keySet()
    {
        final Set<DataRegistryKey<T>> keys = new LinkedHashSet<>();
        for (String id : entries.keySet())
        {
            keys.add(DataRegistryKey.of(registryKey, id, type));
        }
        return Collections.unmodifiableSet(keys);
    }

    /**
     * A snapshot of the entry values.
     *
     * @return an unmodifiable collection in insertion order
     */
    public synchronized Collection<T> values()
    {
        final List<T> values = new ArrayList<>(entries.size());
        for (RegistryEntry<T> entry : entries.values())
        {
            final T value = entry.getOrNull();
            if (value != null)
            {
                values.add(value);
            }
        }
        return Collections.unmodifiableList(values);
    }

    /**
     * A snapshot of the live entries.
     *
     * @return an unmodifiable list in insertion order
     */
    public synchronized List<RegistryEntry<T>> entries()
    {
        return List.copyOf(entries.values());
    }

    /**
     * Reads one entry value.
     *
     * @param key the entry key, not {@code null} and in this registry
     * @return the value, or {@code null} when absent
     * @throws IllegalArgumentException when the key belongs to another registry
     */
    public synchronized T get(DataRegistryKey<T> key)
    {
        final RegistryEntry<T> entry = entryFor(key);
        return entry != null ? entry.getOrNull() : null;
    }

    /**
     * Reads one entry value by full id.
     *
     * <p>A path without a namespace defaults to {@code minecraft}, matching
     * {@link RegistryKey#parse(String)}. Pass the full id to read an entry in
     * this registry's namespace.</p>
     *
     * @param fullId the full {@code namespace:path} id, not {@code null}
     * @return the value, or {@code null} when absent
     * @throws IllegalArgumentException when the id is invalid
     */
    public synchronized T get(String fullId)
    {
        Objects.requireNonNull(fullId, "fullId");
        final RegistryEntry<T> entry = entries.get(RegistryKey.parse(fullId).id());
        return entry != null ? entry.getOrNull() : null;
    }

    /**
     * Optional form of {@link #get(DataRegistryKey)}.
     *
     * @param key the entry key, not {@code null} and in this registry
     * @return the value, or an empty optional
     */
    public Optional<T> getOptional(DataRegistryKey<T> key)
    {
        return Optional.ofNullable(get(key));
    }

    /**
     * Optional form of {@link #get(String)}.
     *
     * @param fullId the full entry id, not {@code null}
     * @return the value, or an empty optional
     */
    public Optional<T> getOptional(String fullId)
    {
        return Optional.ofNullable(get(fullId));
    }

    /**
     * Whether an entry exists.
     *
     * @param key the entry key, not {@code null} and in this registry
     * @return {@code true} when the entry exists
     */
    public synchronized boolean containsKey(DataRegistryKey<T> key)
    {
        return entryFor(key) != null;
    }

    /**
     * Whether an entry exists.
     *
     * @param fullId the full entry id, not {@code null}
     * @return {@code true} when the entry exists
     */
    public synchronized boolean containsKey(String fullId)
    {
        Objects.requireNonNull(fullId, "fullId");
        return entries.containsKey(RegistryKey.parse(fullId).id());
    }

    /**
     * Reads the live entry handle.
     *
     * @param key the entry key, not {@code null} and in this registry
     * @return the entry, or an empty optional
     */
    public synchronized Optional<RegistryEntry<T>> getEntry(DataRegistryKey<T> key)
    {
        return Optional.ofNullable(entryFor(key));
    }

    /**
     * Reads the live entry handle by full id.
     *
     * @param fullId the full entry id, not {@code null}
     * @return the entry, or an empty optional
     */
    public synchronized Optional<RegistryEntry<T>> getEntry(String fullId)
    {
        Objects.requireNonNull(fullId, "fullId");
        return Optional.ofNullable(entries.get(RegistryKey.parse(fullId).id()));
    }

    /**
     * Resolves one tag to an immutable view.
     *
     * @param key the tag key, not {@code null} and in this registry
     * @return the resolved view, never {@code null}
     * @throws RegistryException when the tag belongs to another registry
     */
    public TagView<T> tag(TagKey<T> key)
    {
        return tagStore.resolve(key);
    }

    /**
     * The writable tag store for this registry.
     *
     * <p>The store is frozen when this registry is frozen.</p>
     *
     * @return the tag store, never {@code null}
     */
    public TagStore<T> tags()
    {
        return tagStore;
    }

    /**
     * Convenience delegate for {@link TagStore#add(TagKey, Collection, int,
     * String)}.
     *
     * @param key the tag key, not {@code null} and in this registry
     * @param values the values to add, not {@code null}
     * @param priority the layer priority
     * @param source the diagnostic source label, not {@code null}
     * @throws RegistryException when the registry is frozen
     */
    public void addTag(TagKey<T> key, Collection<T> values, int priority, String source)
    {
        tagStore.add(key, values, priority, source);
    }

    /**
     * Freezes this registry permanently and freezes its tag store.
     *
     * <p>Calling this method more than once has no further effect. Reads remain
     * available after freezing.</p>
     */
    public void freeze()
    {
        synchronized (this)
        {
            if (frozen)
            {
                return;
            }
            frozen = true;
            tagStore.freeze();
        }
    }

    private RegistryEntry<T> entryFor(DataRegistryKey<T> key)
    {
        Objects.requireNonNull(key, "key");
        validateKey(key);
        return entries.get(key.id().id());
    }

    private void validateKey(DataRegistryKey<T> key)
    {
        if (!registryKey.equals(key.registry()))
        {
            throw new IllegalArgumentException("Data registry key " + key
                    + " does not belong to registry " + registryKey.id());
        }
        if (key.type() != type)
        {
            throw new IllegalArgumentException("Data registry key " + key
                    + " uses type " + key.type().getName() + ", not " + type.getName());
        }
    }

    private void ensureWritable()
    {
        if (frozen)
        {
            throw new RegistryException("Data registry " + id() + " is frozen");
        }
    }

    private static String describe(Throwable failure)
    {
        if (failure == null)
        {
            return "unknown failure";
        }
        final String message = failure.getMessage();
        return message != null ? message : failure.getClass().getName();
    }
}
