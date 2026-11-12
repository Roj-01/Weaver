package net.weaver.api.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable snapshot of the data contributed by one data pack layer.
 *
 * <p>Entries are grouped by registry id and then by entry path. Tags are
 * grouped by registry id and then by tag path. All maps, and the nested data
 * maps, are unmodifiable copies made when the snapshot is built.</p>
 */
public final class DataPackSnapshot
{
    /**
     * One value or tag reference inside a tag declaration.
     *
     * <p>A value id is a normal entry id. When {@code tagReference} is true,
     * the id names another tag in the same registry and must not include a
     * leading {@code #}.</p>
     *
     * @param id the entry or tag id
     * @param tagReference whether the id names a tag reference
     * @param required whether a missing value is a hard problem
     */
    public record TagValue(String id, boolean tagReference, boolean required)
    {
        /** Validates the id. */
        public TagValue
        {
            Objects.requireNonNull(id, "id");
        }

        /**
         * Creates a direct entry value.
         *
         * @param id the entry id
         * @param required whether the value is required
         * @return a value with {@code tagReference} set to {@code false}
         */
        public static TagValue value(String id, boolean required)
        {
            return new TagValue(id, false, required);
        }

        /**
         * Creates a tag reference.
         *
         * @param id the referenced tag id, without a leading {@code #}
         * @param required whether the reference is required
         * @return a value with {@code tagReference} set to {@code true}
         */
        public static TagValue reference(String id, boolean required)
        {
            return new TagValue(id, true, required);
        }
    }

    /**
     * One parsed tag file.
     *
     * @param values the declared values and references, in file order
     * @param priority the effective priority of this declaration
     * @param replace whether this declaration replaces lower-priority layers
     */
    public record TagDeclaration(List<TagValue> values, int priority, boolean replace)
    {
        /** Copies the value list. */
        public TagDeclaration
        {
            Objects.requireNonNull(values, "values");
            values = List.copyOf(values);
        }
    }

    private final String packId;
    private final int priority;
    private final Map<String, Map<String, Map<String, Object>>> entries;
    private final Map<String, Map<String, TagDeclaration>> tags;

    private DataPackSnapshot(Builder builder)
    {
        this.packId = builder.packId;
        this.priority = builder.priority;

        final Map<String, Map<String, Map<String, Object>>> entryCopy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Map<String, Object>>> registry
                : builder.entries.entrySet())
        {
            final Map<String, Map<String, Object>> byPath = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> entry : registry.getValue().entrySet())
            {
                byPath.put(entry.getKey(), immutableData(entry.getValue()));
            }
            entryCopy.put(registry.getKey(), Collections.unmodifiableMap(byPath));
        }
        this.entries = Collections.unmodifiableMap(entryCopy);

        final Map<String, Map<String, TagDeclaration>> tagCopy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, TagDeclaration>> registry
                : builder.tags.entrySet())
        {
            tagCopy.put(registry.getKey(),
                    Collections.unmodifiableMap(new LinkedHashMap<>(registry.getValue())));
        }
        this.tags = Collections.unmodifiableMap(tagCopy);
    }

    /**
     * Starts building a snapshot for one layer.
     *
     * @param packId the pack identifier, not {@code null}
     * @param priority the layer priority
     * @return a new builder
     */
    public static Builder builder(String packId, int priority)
    {
        return new Builder(packId, priority);
    }

    /**
     * The pack identifier used as the source of loaded entries and tags.
     *
     * @return the pack id, never {@code null}
     */
    public String packId()
    {
        return packId;
    }

    /**
     * The default priority of this layer.
     *
     * @return the layer priority
     */
    public int priority()
    {
        return priority;
    }

    /**
     * Registry entries, grouped by registry id and then by entry path.
     *
     * @return an unmodifiable map of registry id to entry path to parsed object
     */
    public Map<String, Map<String, Map<String, Object>>> entries()
    {
        return entries;
    }

    /**
     * Tag declarations, grouped by registry id and then by tag path.
     *
     * @return an unmodifiable map of registry id to tag path to declaration
     */
    public Map<String, Map<String, TagDeclaration>> tags()
    {
        return tags;
    }

    private static Map<String, Object> immutableData(Map<String, Object> data)
    {
        Objects.requireNonNull(data, "data");
        final Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet())
        {
            copy.put(Objects.requireNonNull(entry.getKey(), "data key"),
                    immutableValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value)
    {
        if (value instanceof Map<?, ?> map)
        {
            final Map<Object, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet())
            {
                copy.put(Objects.requireNonNull(entry.getKey(), "map key"),
                        immutableValue(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list)
        {
            final java.util.ArrayList<Object> copy = new java.util.ArrayList<>(list.size());
            for (Object element : list)
            {
                copy.add(immutableValue(element));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    /**
     * Builds an immutable snapshot.
     */
    public static final class Builder
    {
        private final String packId;
        private final int priority;
        private final Map<String, Map<String, Map<String, Object>>> entries =
                new LinkedHashMap<>();
        private final Map<String, Map<String, TagDeclaration>> tags = new LinkedHashMap<>();

        private Builder(String packId, int priority)
        {
            this.packId = Objects.requireNonNull(packId, "packId");
            this.priority = priority;
        }

        /**
         * Adds one registry entry. Adding the same registry id and path twice
         * keeps the last value.
         *
         * @param registryId the target registry id, not {@code null}
         * @param entryPath the entry path inside that registry, not {@code null}
         * @param data the parsed JSON object, not {@code null}
         * @return this builder
         */
        public Builder entry(String registryId, String entryPath, Map<String, Object> data)
        {
            Objects.requireNonNull(registryId, "registryId");
            Objects.requireNonNull(entryPath, "entryPath");
            Objects.requireNonNull(data, "data");
            entries.computeIfAbsent(registryId, ignored -> new LinkedHashMap<>())
                    .put(entryPath, data);
            return this;
        }

        /**
         * Adds one tag declaration. Adding the same registry id and tag path
         * twice keeps the last declaration.
         *
         * @param registryId the target registry id, not {@code null}
         * @param tagPath the tag path inside that registry, not {@code null}
         * @param declaration the parsed declaration, not {@code null}
         * @return this builder
         */
        public Builder tag(String registryId, String tagPath, TagDeclaration declaration)
        {
            Objects.requireNonNull(registryId, "registryId");
            Objects.requireNonNull(tagPath, "tagPath");
            Objects.requireNonNull(declaration, "declaration");
            tags.computeIfAbsent(registryId, ignored -> new LinkedHashMap<>())
                    .put(tagPath, declaration);
            return this;
        }

        /**
         * Builds an immutable snapshot from the current contents.
         *
         * @return a new snapshot
         */
        public DataPackSnapshot build()
        {
            return new DataPackSnapshot(this);
        }
    }
}
