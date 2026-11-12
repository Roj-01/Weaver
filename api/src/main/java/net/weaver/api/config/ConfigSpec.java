package net.weaver.api.config;

import net.weaver.api.event.Side;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The immutable declaration of one mod's configuration.
 *
 * <p>A spec contains the mod id, the side it belongs to, the current schema
 * version, an optional description, the declared values in declaration order,
 * the migrations that upgrade older files, and the client-server sync policy.
 * Instances are created by {@link ConfigBuilder} and never change after
 * {@link ConfigBuilder#build()}.
 *
 * <p>The {@link ConfigValue} instances are live: their declarations are fixed,
 * but their current values can change through {@link ConfigValue#set(Object)}.
 */
public final class ConfigSpec
{
    private final String modId;
    private final Side side;
    private final int schemaVersion;
    private final String description;
    private final List<ConfigValue<?>> values;
    private final Map<String, ConfigValue<?>> byKey;
    private final Set<String> keySet;
    private final List<ConfigMigration> migrations;
    private final ConfigSyncPolicy syncPolicy;

    ConfigSpec(String modId, Side side, int schemaVersion, String description,
               List<ConfigValue<?>> values, List<ConfigMigration> migrations)
    {
        this(modId, side, schemaVersion, description, values, migrations,
                ConfigSyncPolicy.NONE);
    }

    ConfigSpec(String modId, Side side, int schemaVersion, String description,
               List<ConfigValue<?>> values, List<ConfigMigration> migrations,
               ConfigSyncPolicy syncPolicy)
    {
        this.modId = Objects.requireNonNull(modId, "modId");
        this.side = Objects.requireNonNull(side, "side");
        if (schemaVersion < 1)
        {
            throw new IllegalArgumentException("Config schema version must be >= 1 but was "
                    + schemaVersion);
        }
        this.schemaVersion = schemaVersion;
        this.description = Objects.requireNonNull(description, "description");
        this.syncPolicy = Objects.requireNonNull(syncPolicy, "syncPolicy");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(migrations, "migrations");
        List<ConfigValue<?>> copiedValues = List.copyOf(values);
        LinkedHashMap<String, ConfigValue<?>> indexed = new LinkedHashMap<>();
        for (ConfigValue<?> value : copiedValues)
        {
            if (indexed.putIfAbsent(value.key(), value) != null)
            {
                throw new ConfigException("Duplicate config key '" + value.key()
                        + "' for mod '" + modId + "'");
            }
        }
        this.values = copiedValues;
        this.byKey = Collections.unmodifiableMap(indexed);
        this.keySet = Collections.unmodifiableSet(new LinkedHashSet<>(indexed.keySet()));
        List<ConfigMigration> sorted = new ArrayList<>(migrations);
        sorted.sort(Comparator.comparingInt(ConfigMigration::fromVersion));
        Set<Integer> seen = new HashSet<>();
        for (ConfigMigration migration : sorted)
        {
            if (!seen.add(migration.fromVersion()))
            {
                throw new ConfigException("Duplicate config migration fromVersion "
                        + migration.fromVersion() + " for mod '" + modId + "'");
            }
        }
        this.migrations = List.copyOf(sorted);
    }

    /**
     * The id of the mod that owns this configuration.
     *
     * @return the mod id, never {@code null}
     */
    public String modId()
    {
        return modId;
    }

    /**
     * The side this configuration belongs to.
     *
     * @return the side, never {@code null}
     */
    public Side side()
    {
        return side;
    }

    /**
     * The schema version written by {@link ConfigManager#save()}.
     *
     * @return the schema version, always at least {@code 1}
     */
    public int schemaVersion()
    {
        return schemaVersion;
    }

    /**
     * The optional human-readable description of this configuration.
     *
     * @return the description, never {@code null}, possibly empty
     */
    public String description()
    {
        return description;
    }

    /**
     * Every declared value in declaration order.
     *
     * @return an immutable list of values, never {@code null}
     */
    public List<ConfigValue<?>> values()
    {
        return values;
    }

    /**
     * Every declared key in declaration order.
     *
     * @return an immutable set of keys, never {@code null}
     */
    public Set<String> keySet()
    {
        return keySet;
    }

    /**
     * The number of declared values.
     *
     * @return the number of values
     */
    public int size()
    {
        return values.size();
    }

    /**
     * Looks up a value by its declared key.
     *
     * @param key the dotted configuration key, not {@code null}
     * @return the matching value, never {@code null}
     * @throws ConfigException when no value uses {@code key}; the message lists
     *                         the known keys
     */
    public ConfigValue<?> value(String key)
    {
        Objects.requireNonNull(key, "key");
        ConfigValue<?> value = byKey.get(key);
        if (value == null)
        {
            throw new ConfigException("Unknown config key '" + key + "' for mod '" + modId
                    + "'; known keys: " + keySet);
        }
        return value;
    }

    /**
     * Looks up a value and checks that its current value is an instance of the
     * requested Java type.
     *
     * <p>This is useful for {@link Boolean}, {@link Integer}, {@link Long},
     * {@link Double}, {@link String}, and enum classes. For list values the
     * stored value is a {@link List}, so a list key matches {@code List.class}
     * but not its element class.
     *
     * @param key  the dotted configuration key, not {@code null}
     * @param type the expected Java value class, not {@code null}
     * @param <T>  the expected value type
     * @return the matching value, never {@code null}
     * @throws ConfigException when the key is absent or the current value is
     *                         not an instance of {@code type}
     */
    @SuppressWarnings("unchecked")
    public <T> ConfigValue<T> value(String key, Class<T> type)
    {
        Objects.requireNonNull(type, "type");
        ConfigValue<?> candidate = value(key);
        Object current = candidate.get();
        if (!type.isInstance(current))
        {
            throw new ConfigException("Config key '" + key + "' for mod '" + modId
                    + "' has type " + candidate.typeName() + " (Java "
                    + current.getClass().getName() + "), not " + type.getName());
        }
        return (ConfigValue<T>) candidate;
    }

    /**
     * Looks up a value without throwing when the key is absent.
     *
     * @param key the dotted configuration key, not {@code null}
     * @return the matching value, or an empty optional
     */
    public Optional<ConfigValue<?>> find(String key)
    {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(byKey.get(key));
    }

    /**
     * Every migration registered for this spec, sorted by ascending
     * {@link ConfigMigration#fromVersion()}.
     *
     * @return an immutable list of migrations, never {@code null}
     */
    public List<ConfigMigration> migrations()
    {
        return migrations;
    }

    /**
     * The client-server synchronization policy declared for this spec.
     *
     * @return the policy, never {@code null}; {@link ConfigSyncPolicy#NONE} by
     *         default
     */
    public ConfigSyncPolicy syncPolicy()
    {
        return syncPolicy;
    }
}
