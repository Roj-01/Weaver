package net.weaver.api.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A writable, priority-aware tag store for one data registry.
 *
 * <p>Every call to {@link #add(TagKey, Collection, int, String)},
 * {@link #addReference(TagKey, TagKey, int, String)}, or
 * {@link #replace(TagKey, Collection, int, String)} appends one layer. Layers
 * are merged by descending priority and then by ascending insertion sequence.
 * Values are deduplicated by {@link Object#equals(Object)} while preserving the
 * first occurrence. Tag references are expanded depth-first with a visited set,
 * so recursive references terminate. A missing referenced tag is treated as an
 * empty tag and ignored.</p>
 *
 * <p>After the first layer with {@code replace} set, layers with a strictly
 * lower priority are ignored. Layers at the same priority are still merged.</p>
 *
 * <p>All methods are synchronized. Once {@link #freeze()} succeeds, every
 * mutator throws {@link RegistryException}; the store cannot be unfrozen.
 * Instances are created by {@link DataRegistry#tags()}.</p>
 *
 * @param <T> the registry entry type
 */
public final class TagStore<T>
{
    /**
     * One tag declaration. Values are read through suppliers at resolve time so a
     * pack-loaded layer follows its entries' current values (an entry replaced by
     * a higher-priority pack is not left behind as a stale object).
     */
    private record Layer<T>(List<Supplier<? extends T>> values, List<TagKey<T>> references,
                            int priority, long sequence, boolean replace, String source,
                            boolean fromPack)
    {
    }

    private final RegistryKey<?> registryKey;
    private final Map<TagKey<T>, List<Layer<T>>> layers = new LinkedHashMap<>();
    private long sequence;
    private boolean frozen;

    TagStore(RegistryKey<?> registryKey)
    {
        this.registryKey = Objects.requireNonNull(registryKey, "registryKey");
    }

    /**
     * Adds a layer of direct values.
     *
     * @param key the tag to add to, not {@code null} and in this store's
     *            registry
     * @param values the values to add, not {@code null}
     * @param priority the layer priority
     * @param source a diagnostic source label, not {@code null}
     * @throws RegistryException when the store is frozen or the tag belongs to
     *                           another registry
     */
    public synchronized void add(TagKey<T> key, Collection<T> values, int priority,
                                 String source)
    {
        ensureWritable();
        Objects.requireNonNull(values, "values");
        putLayer(key, List.copyOf(values), List.of(), priority, source, false);
    }

    /**
     * Adds a layer containing one tag reference.
     *
     * @param key the tag to add to, not {@code null} and in this store's
     *            registry
     * @param reference the referenced tag, not {@code null} and in this store's
     *                  registry
     * @param priority the layer priority
     * @param source a diagnostic source label, not {@code null}
     * @throws RegistryException when the store is frozen or either tag belongs
     *                           to another registry
     */
    public synchronized void addReference(TagKey<T> key, TagKey<T> reference, int priority,
                                          String source)
    {
        ensureWritable();
        Objects.requireNonNull(reference, "reference");
        putLayer(key, List.of(), List.of(validateKey(reference)), priority, source, false);
    }

    /**
     * Adds a replacing layer of direct values.
     *
     * <p>Lower-priority layers for the tag are ignored by the next
     * {@link #resolve(TagKey)} call. A replacing layer with an empty value
     * collection clears lower-priority values.</p>
     *
     * @param key the tag to replace, not {@code null} and in this store's
     *            registry
     * @param values the replacing values, not {@code null}
     * @param priority the layer priority
     * @param source a diagnostic source label, not {@code null}
     * @throws RegistryException when the store is frozen or the tag belongs to
     *                           another registry
     */
    public synchronized void replace(TagKey<T> key, Collection<T> values, int priority,
                                     String source)
    {
        ensureWritable();
        Objects.requireNonNull(values, "values");
        putLayer(key, List.copyOf(values), List.of(), priority, source, true);
    }

    /** Adds a pack-loaded layer whose values are resolved live from entry handles. */
    synchronized void addPackLayer(TagKey<T> key, List<Supplier<? extends T>> values,
                                   List<TagKey<T>> references, int priority, String source,
                                   boolean replace)
    {
        putLayer(key, values, references, priority, source, replace, true);
    }

    /** Removes every pack-loaded layer so a reload can re-apply packs from scratch. */
    synchronized int removePackLayers()
    {
        ensureWritable();
        int removed = 0;
        for (List<Layer<T>> tagLayers : layers.values())
        {
            final int before = tagLayers.size();
            tagLayers.removeIf(Layer::fromPack);
            removed += before - tagLayers.size();
        }
        layers.values().removeIf(List::isEmpty);
        return removed;
    }

    /**
     * Resolves one tag to an immutable view.
     *
     * <p>A tag with no layers resolves to an empty view. The returned view is a
     * snapshot; later mutations of this store do not change it.</p>
     *
     * @param key the tag to resolve, not {@code null} and in this store's
     *            registry
     * @return the resolved view, never {@code null}
     * @throws RegistryException when the tag belongs to another registry
     */
    public synchronized TagView<T> resolve(TagKey<T> key)
    {
        final TagKey<T> checked = validateKey(key);
        final Set<TagKey<T>> visited = new HashSet<>();
        final LinkedHashSet<T> resolved = new LinkedHashSet<>();
        resolveInto(checked, visited, resolved);
        return new TagView<>(checked, List.copyOf(resolved));
    }

    /**
     * The tags that have at least one layer.
     *
     * @return an unmodifiable snapshot of the tag keys
     */
    public synchronized Set<TagKey<T>> keys()
    {
        return Collections.unmodifiableSet(new LinkedHashSet<>(layers.keySet()));
    }

    /**
     * Whether this store rejects mutations.
     *
     * @return {@code true} after a successful {@link #freeze()}
     */
    public synchronized boolean isFrozen()
    {
        return frozen;
    }

    /**
     * Freezes this store permanently. Calling this method more than once has no
     * further effect.
     */
    public synchronized void freeze()
    {
        frozen = true;
    }

    private void putLayer(TagKey<T> key, List<T> values, List<TagKey<T>> references,
                          int priority, String source, boolean replace)
    {
        Objects.requireNonNull(values, "values");
        final List<Supplier<? extends T>> constants = new ArrayList<>(values.size());
        for (T value : values)
        {
            Objects.requireNonNull(value, "tag value");
            constants.add(() -> value);
        }
        putLayer(key, constants, references, priority, source, replace, false);
    }

    private void putLayer(TagKey<T> key, List<Supplier<? extends T>> values,
                          List<TagKey<T>> references, int priority, String source,
                          boolean replace, boolean fromPack)
    {
        ensureWritable();
        final TagKey<T> checkedKey = validateKey(key);
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(references, "references");
        Objects.requireNonNull(source, "source");
        for (TagKey<T> reference : references)
        {
            validateKey(reference);
        }
        layers.computeIfAbsent(checkedKey, ignored -> new ArrayList<>())
                .add(new Layer<>(List.copyOf(values), List.copyOf(references), priority,
                        sequence++, replace, source, fromPack));
    }

    private void resolveInto(TagKey<T> key, Set<TagKey<T>> visited, LinkedHashSet<T> resolved)
    {
        if (!visited.add(key))
        {
            return;
        }
        final List<Layer<T>> tagLayers = layers.get(key);
        if (tagLayers == null)
        {
            return;
        }
        final List<Layer<T>> ordered = new ArrayList<>(tagLayers);
        ordered.sort(Comparator.comparingInt(Layer<T>::priority).reversed()
                .thenComparingLong(Layer<T>::sequence));
        boolean replacing = false;
        int replacedPriority = 0;
        for (Layer<T> layer : ordered)
        {
            if (replacing && layer.priority < replacedPriority)
            {
                continue;
            }
            if (layer.replace)
            {
                replacing = true;
                replacedPriority = layer.priority;
            }
            for (Supplier<? extends T> value : layer.values)
            {
                final T current = value.get();
                if (current != null)
                {
                    resolved.add(current);
                }
            }
            for (TagKey<T> reference : layer.references)
            {
                resolveInto(reference, visited, resolved);
            }
        }
    }

    private TagKey<T> validateKey(TagKey<T> key)
    {
        Objects.requireNonNull(key, "key");
        if (!registryKey.equals(key.registry()))
        {
            throw new RegistryException("Tag " + key + " belongs to registry "
                    + key.registry().id() + ", not " + registryKey.id());
        }
        return key;
    }

    private void ensureWritable()
    {
        if (frozen)
        {
            throw new RegistryException("Tag store for registry " + registryKey.id()
                    + " is frozen");
        }
    }
}
