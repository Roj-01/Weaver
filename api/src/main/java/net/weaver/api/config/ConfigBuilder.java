package net.weaver.api.config;

import net.weaver.api.event.Side;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Builds an immutable {@link ConfigSpec}.
 *
 * <p>A builder is created with {@link #create(String, Side)}. Values are
 * declared with the typed factory methods, which validate the key, bounds, and
 * default immediately. Duplicate keys are rejected as soon as the second
 * declaration is attempted. After {@link #build()} succeeds, every mutating
 * builder method throws {@link IllegalStateException}; {@link #build()} may be
 * called repeatedly and returns the same spec.
 *
 * <p>An out-of-range or otherwise invalid default raises
 * {@link ConfigValidationException} with the declared key path. Invalid
 * arguments other than value defaults (a malformed key, an impossible bound,
 * an enum class that is not an enum) raise {@link IllegalArgumentException}.
 *
 * <p>Builders are not thread-safe. Build one during mod initialization and
 * publish the resulting spec.
 */
public final class ConfigBuilder
{
    private static final Pattern MOD_ID = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*");

    private final String modId;
    private final Side side;
    private final LinkedHashMap<String, ConfigValue<?>> values = new LinkedHashMap<>();
    private final List<ConfigMigration> migrations = new ArrayList<>();
    private int schemaVersion = 1;
    private String description = "";
    private ConfigSyncPolicy syncPolicy = ConfigSyncPolicy.NONE;
    private boolean built;
    private ConfigSpec spec;

    private ConfigBuilder(String modId, Side side)
    {
        this.modId = modId;
        this.side = side;
    }

    /**
     * Creates a builder for one mod and side.
     *
     * @param modId the mod id, matching {@code [a-z0-9_.-]+}, not {@code null}
     * @param side  the side the configuration belongs to, not {@code null}
     * @return a new mutable builder
     * @throws NullPointerException     when {@code modId} or {@code side} is
     *                                  {@code null}
     * @throws IllegalArgumentException when {@code modId} does not match
     *                                  {@code [a-z0-9_.-]+}
     */
    public static ConfigBuilder create(String modId, Side side)
    {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(side, "side");
        if (!MOD_ID.matcher(modId).matches())
        {
            throw new IllegalArgumentException("Invalid config mod id '" + modId
                    + "': expected [a-z0-9_.-]+");
        }
        return new ConfigBuilder(modId, side);
    }

    /**
     * Sets the schema version written by the manager.
     *
     * @param version the schema version, at least {@code 1}
     * @return this builder
     * @throws IllegalArgumentException when {@code version} is less than
     *                                  {@code 1}
     * @throws IllegalStateException    when the spec has already been built
     */
    public ConfigBuilder schemaVersion(int version)
    {
        ensureMutable();
        if (version < 1)
        {
            throw new IllegalArgumentException("Config schema version for mod '" + modId
                    + "' must be >= 1 but was " + version);
        }
        this.schemaVersion = version;
        return this;
    }

    /**
     * Sets the human-readable description of the configuration.
     *
     * @param description the description, not {@code null}, possibly empty
     * @return this builder
     * @throws NullPointerException  when {@code description} is {@code null}
     * @throws IllegalStateException when the spec has already been built
     */
    public ConfigBuilder description(String description)
    {
        ensureMutable();
        this.description = Objects.requireNonNull(description, "description");
        return this;
    }

    /**
     * Sets the client-server synchronization policy.
     *
     * <p>Only {@link ConfigSyncPolicy#SERVER_TO_CLIENT} sends the server's
     * loaded document to a connecting client; the client applies it read-only
     * and must not write it. The default is {@link ConfigSyncPolicy#NONE}, which
     * keeps the configuration local. Declare the same policy on the client and
     * the server specs when the values should travel.</p>
     *
     * @param policy the policy, not {@code null}
     * @return this builder
     * @throws NullPointerException  when {@code policy} is {@code null}
     * @throws IllegalStateException when the spec has already been built
     */
    public ConfigBuilder sync(ConfigSyncPolicy policy)
    {
        ensureMutable();
        this.syncPolicy = Objects.requireNonNull(policy, "policy");
        return this;
    }

    /**
     * Registers a migration that upgrades a document from
     * {@link ConfigMigration#fromVersion()} to the next version.
     *
     * <p>Migrations may be added in any order; the resulting spec sorts them by
     * ascending {@code fromVersion}. Duplicate {@code fromVersion} values are
     * rejected by {@link #build()}.
     *
     * @param migration the migration to register, not {@code null}
     * @return this builder
     * @throws NullPointerException  when {@code migration} is {@code null}
     * @throws IllegalStateException when the spec has already been built
     */
    public ConfigBuilder migration(ConfigMigration migration)
    {
        ensureMutable();
        Objects.requireNonNull(migration, "migration");
        migrations.add(migration);
        return this;
    }

    /**
     * Declares a boolean value.
     *
     * @param key          the dotted key, not {@code null}
     * @param defaultValue the default, used when the file omits the key
     * @param description  the description, not {@code null}, possibly empty
     * @return the live value handle
     * @throws IllegalArgumentException when the key is malformed
     * @throws ConfigException          when the key is declared twice
     * @throws IllegalStateException    when the spec has already been built
     */
    public ConfigValue<Boolean> booleanValue(String key, boolean defaultValue, String description)
    {
        beginValue(key);
        requireDescription(description);
        return register(new ConfigValue<Boolean>(modId, key, description, ConfigType.BOOLEAN,
                defaultValue, null, null, null, null, null));
    }

    /**
     * Declares an unbounded integer value.
     *
     * @param key          the dotted key, not {@code null}
     * @param defaultValue the default, used when the file omits the key
     * @param description  the description, not {@code null}, possibly empty
     * @return the live value handle
     * @throws IllegalArgumentException when the key is malformed
     * @throws ConfigException          when the key is declared twice
     * @throws IllegalStateException    when the spec has already been built
     */
    public ConfigValue<Integer> intValue(String key, int defaultValue, String description)
    {
        beginValue(key);
        requireDescription(description);
        return register(new ConfigValue<Integer>(modId, key, description, ConfigType.INT,
                defaultValue, null, null, null, null, null));
    }

    /**
     * Declares an integer value with inclusive bounds.
     *
     * @param key          the dotted key, not {@code null}
     * @param defaultValue the default, which must satisfy the bounds
     * @param min          the inclusive minimum
     * @param max          the inclusive maximum
     * @param description  the description, not {@code null}, possibly empty
     * @return the live value handle
     * @throws IllegalArgumentException when the key is malformed or
     *                                  {@code min > max}
     * @throws ConfigValidationException when the default is outside the bounds
     * @throws ConfigException          when the key is declared twice
     * @throws IllegalStateException    when the spec has already been built
     */
    public ConfigValue<Integer> intValue(String key, int defaultValue, int min, int max,
                                         String description)
    {
        beginValue(key);
        requireDescription(description);
        requireRange(key, min, max);
        return register(new ConfigValue<Integer>(modId, key, description, ConfigType.INT,
                defaultValue, min, max, null, null, null));
    }

    /**
     * Declares an unbounded long value.
     *
     * @param key          the dotted key, not {@code null}
     * @param defaultValue the default, used when the file omits the key
     * @param description  the description, not {@code null}, possibly empty
     * @return the live value handle
     * @throws IllegalArgumentException when the key is malformed
     * @throws ConfigException          when the key is declared twice
     * @throws IllegalStateException    when the spec has already been built
     */
    public ConfigValue<Long> longValue(String key, long defaultValue, String description)
    {
        beginValue(key);
        requireDescription(description);
        return register(new ConfigValue<Long>(modId, key, description, ConfigType.LONG,
                defaultValue, null, null, null, null, null));
    }

    /**
     * Declares a long value with inclusive bounds.
     *
     * @param key          the dotted key, not {@code null}
     * @param defaultValue the default, which must satisfy the bounds
     * @param min          the inclusive minimum
     * @param max          the inclusive maximum
     * @param description  the description, not {@code null}, possibly empty
     * @return the live value handle
     * @throws IllegalArgumentException when the key is malformed or
     *                                  {@code min > max}
     * @throws ConfigValidationException when the default is outside the bounds
     * @throws ConfigException          when the key is declared twice
     * @throws IllegalStateException    when the spec has already been built
     */
    public ConfigValue<Long> longValue(String key, long defaultValue, long min, long max,
                                       String description)
    {
        beginValue(key);
        requireDescription(description);
        requireRange(key, min, max);
        return register(new ConfigValue<Long>(modId, key, description, ConfigType.LONG,
                defaultValue, min, max, null, null, null));
    }

    /**
     * Declares an unbounded decimal value.
     *
     * @param key          the dotted key, not {@code null}
     * @param defaultValue the default, which must be finite
     * @param description  the description, not {@code null}, possibly empty
     * @return the live value handle
     * @throws IllegalArgumentException when the key is malformed
     * @throws ConfigValidationException when the default is not finite
     * @throws ConfigException          when the key is declared twice
     * @throws IllegalStateException    when the spec has already been built
     */
    public ConfigValue<Double> doubleValue(String key, double defaultValue, String description)
    {
        beginValue(key);
        requireDescription(description);
        return register(new ConfigValue<Double>(modId, key, description, ConfigType.DOUBLE,
                defaultValue, null, null, null, null, null));
    }

    /**
     * Declares a decimal value with inclusive finite bounds.
     *
     * @param key          the dotted key, not {@code null}
     * @param defaultValue the default, which must be finite and satisfy the
     *                     bounds
     * @param min          the inclusive minimum, which must be finite
     * @param max          the inclusive maximum, which must be finite
     * @param description  the description, not {@code null}, possibly empty
     * @return the live value handle
     * @throws IllegalArgumentException when the key is malformed, a bound is
     *                                  not finite, or {@code min > max}
     * @throws ConfigValidationException when the default is not finite or is
     *                                   outside the bounds
     * @throws ConfigException          when the key is declared twice
     * @throws IllegalStateException    when the spec has already been built
     */
    public ConfigValue<Double> doubleValue(String key, double defaultValue, double min, double max,
                                           String description)
    {
        beginValue(key);
        requireDescription(description);
        requireRange(key, min, max);
        return register(new ConfigValue<Double>(modId, key, description, ConfigType.DOUBLE,
                defaultValue, min, max, null, null, null));
    }

    /**
     * Declares an unbounded string value.
     *
     * @param key          the dotted key, not {@code null}
     * @param defaultValue the default, not {@code null}
     * @param description  the description, not {@code null}, possibly empty
     * @return the live value handle
     * @throws IllegalArgumentException when the key is malformed
     * @throws ConfigValidationException when {@code defaultValue} is {@code null}
     * @throws ConfigException          when the key is declared twice
     * @throws IllegalStateException    when the spec has already been built
     */
    public ConfigValue<String> stringValue(String key, String defaultValue, String description)
    {
        beginValue(key);
        requireDescription(description);
        return register(new ConfigValue<String>(modId, key, description, ConfigType.STRING,
                defaultValue, null, null, null, null, null));
    }

    /**
     * Declares a string value with inclusive length bounds.
     *
     * @param key          the dotted key, not {@code null}
     * @param defaultValue the default, not {@code null} and within the length
     *                     bounds
     * @param minLength    the inclusive minimum length, at least {@code 0}
     * @param maxLength    the inclusive maximum length, at least
     *                     {@code minLength}
     * @param description  the description, not {@code null}, possibly empty
     * @return the live value handle
     * @throws IllegalArgumentException when the key is malformed or the length
     *                                  bounds are invalid
     * @throws ConfigValidationException when the default is {@code null} or its
     *                                   length is outside the bounds
     * @throws ConfigException          when the key is declared twice
     * @throws IllegalStateException    when the spec has already been built
     */
    public ConfigValue<String> stringValue(String key, String defaultValue, int minLength,
                                           int maxLength, String description)
    {
        beginValue(key);
        requireDescription(description);
        requireSizeRange(key, minLength, maxLength, "length");
        return register(new ConfigValue<String>(modId, key, description, ConfigType.STRING,
                defaultValue, null, null, minLength, maxLength, null));
    }

    /**
     * Declares an enum value.
     *
     * @param key          the dotted key, not {@code null}
     * @param enumType     the enum class, not {@code null}
     * @param defaultValue the default constant, not {@code null}
     * @param description  the description, not {@code null}, possibly empty
     * @param <E>          the enum type
     * @return the live value handle
     * @throws NullPointerException     when {@code key}, {@code enumType}, or
     *                                  {@code description} is {@code null}
     * @throws IllegalArgumentException when the key is malformed or
     *                                  {@code enumType} is not an enum
     * @throws ConfigValidationException when the default is {@code null} or not
     *                                   a member of {@code enumType}
     * @throws ConfigException          when the key is declared twice
     * @throws IllegalStateException    when the spec has already been built
     */
    public <E extends Enum<E>> ConfigValue<E> enumValue(String key, Class<E> enumType,
                                                        E defaultValue, String description)
    {
        beginValue(key);
        Objects.requireNonNull(enumType, "enumType");
        requireDescription(description);
        requireEnumType(key, enumType);
        return register(new ConfigValue<E>(modId, key, description, ConfigType.ENUM,
                defaultValue, null, null, null, null, enumType));
    }

    /**
     * Declares an unbounded list of strings.
     *
     * @param key          the dotted key, not {@code null}
     * @param defaultValue the default elements, not {@code null} and containing
     *                     no {@code null} elements
     * @param description  the description, not {@code null}, possibly empty
     * @return the live value handle
     * @throws IllegalArgumentException when the key is malformed
     * @throws ConfigValidationException when {@code defaultValue} is {@code null}
     *                                   or an element is {@code null}
     * @throws ConfigException          when the key is declared twice
     * @throws IllegalStateException    when the spec has already been built
     */
    public ConfigValue<List<String>> stringListValue(String key, List<String> defaultValue,
                                                     String description)
    {
        beginValue(key);
        requireDescription(description);
        return register(new ConfigValue<List<String>>(modId, key, description,
                ConfigType.STRING_LIST, defaultValue, null, null, null, null, null));
    }

    /**
     * Declares a list of strings with inclusive size bounds.
     *
     * @param key          the dotted key, not {@code null}
     * @param defaultValue the default elements, not {@code null} and within the
     *                     size bounds
     * @param minSize      the inclusive minimum size, at least {@code 0}
     * @param maxSize      the inclusive maximum size, at least {@code minSize}
     * @param description  the description, not {@code null}, possibly empty
     * @return the live value handle
     * @throws IllegalArgumentException when the key is malformed or the size
     *                                  bounds are invalid
     * @throws ConfigValidationException when {@code defaultValue} is
     *                                   {@code null}, the default size is
     *                                   outside the bounds, or an element is
     *                                   {@code null}
     * @throws ConfigException          when the key is declared twice
     * @throws IllegalStateException    when the spec has already been built
     */
    public ConfigValue<List<String>> stringListValue(String key, List<String> defaultValue,
                                                     int minSize, int maxSize, String description)
    {
        beginValue(key);
        requireDescription(description);
        requireSizeRange(key, minSize, maxSize, "size");
        return register(new ConfigValue<List<String>>(modId, key, description,
                ConfigType.STRING_LIST, defaultValue, null, null, minSize, maxSize, null));
    }

    /**
     * Declares an unbounded list of integers.
     *
     * @param key          the dotted key, not {@code null}
     * @param defaultValue the default elements, not {@code null} and containing
     *                     no {@code null} elements
     * @param description  the description, not {@code null}, possibly empty
     * @return the live value handle
     * @throws IllegalArgumentException when the key is malformed
     * @throws ConfigValidationException when {@code defaultValue} is {@code null}
     *                                   or an element is {@code null}
     * @throws ConfigException          when the key is declared twice
     * @throws IllegalStateException    when the spec has already been built
     */
    public ConfigValue<List<Integer>> intListValue(String key, List<Integer> defaultValue,
                                                   String description)
    {
        beginValue(key);
        requireDescription(description);
        return register(new ConfigValue<List<Integer>>(modId, key, description,
                ConfigType.INT_LIST, defaultValue, null, null, null, null, null));
    }

    /**
     * Declares a list of integers with inclusive size bounds.
     *
     * @param key          the dotted key, not {@code null}
     * @param defaultValue the default elements, not {@code null} and within the
     *                     size bounds
     * @param minSize      the inclusive minimum size, at least {@code 0}
     * @param maxSize      the inclusive maximum size, at least {@code minSize}
     * @param description  the description, not {@code null}, possibly empty
     * @return the live value handle
     * @throws IllegalArgumentException when the key is malformed or the size
     *                                  bounds are invalid
     * @throws ConfigValidationException when {@code defaultValue} is
     *                                   {@code null}, the default size is
     *                                   outside the bounds, or an element is
     *                                   {@code null}
     * @throws ConfigException          when the key is declared twice
     * @throws IllegalStateException    when the spec has already been built
     */
    public ConfigValue<List<Integer>> intListValue(String key, List<Integer> defaultValue,
                                                   int minSize, int maxSize, String description)
    {
        beginValue(key);
        requireDescription(description);
        requireSizeRange(key, minSize, maxSize, "size");
        return register(new ConfigValue<List<Integer>>(modId, key, description,
                ConfigType.INT_LIST, defaultValue, null, null, minSize, maxSize, null));
    }

    /**
     * Declares an unbounded list of longs.
     *
     * @param key          the dotted key, not {@code null}
     * @param defaultValue the default elements, not {@code null} and containing
     *                     no {@code null} elements
     * @param description  the description, not {@code null}, possibly empty
     * @return the live value handle
     * @throws IllegalArgumentException when the key is malformed
     * @throws ConfigValidationException when {@code defaultValue} is {@code null}
     *                                   or an element is {@code null}
     * @throws ConfigException          when the key is declared twice
     * @throws IllegalStateException    when the spec has already been built
     */
    public ConfigValue<List<Long>> longListValue(String key, List<Long> defaultValue,
                                                 String description)
    {
        beginValue(key);
        requireDescription(description);
        return register(new ConfigValue<List<Long>>(modId, key, description,
                ConfigType.LONG_LIST, defaultValue, null, null, null, null, null));
    }

    /**
     * Declares a list of longs with inclusive size bounds.
     *
     * @param key          the dotted key, not {@code null}
     * @param defaultValue the default elements, not {@code null} and within the
     *                     size bounds
     * @param minSize      the inclusive minimum size, at least {@code 0}
     * @param maxSize      the inclusive maximum size, at least {@code minSize}
     * @param description  the description, not {@code null}, possibly empty
     * @return the live value handle
     * @throws IllegalArgumentException when the key is malformed or the size
     *                                  bounds are invalid
     * @throws ConfigValidationException when {@code defaultValue} is
     *                                   {@code null}, the default size is
     *                                   outside the bounds, or an element is
     *                                   {@code null}
     * @throws ConfigException          when the key is declared twice
     * @throws IllegalStateException    when the spec has already been built
     */
    public ConfigValue<List<Long>> longListValue(String key, List<Long> defaultValue,
                                                 int minSize, int maxSize, String description)
    {
        beginValue(key);
        requireDescription(description);
        requireSizeRange(key, minSize, maxSize, "size");
        return register(new ConfigValue<List<Long>>(modId, key, description,
                ConfigType.LONG_LIST, defaultValue, null, null, minSize, maxSize, null));
    }

    /**
     * Declares an unbounded list of finite decimals.
     *
     * @param key          the dotted key, not {@code null}
     * @param defaultValue the default elements, not {@code null} and finite
     * @param description  the description, not {@code null}, possibly empty
     * @return the live value handle
     * @throws IllegalArgumentException when the key is malformed
     * @throws ConfigValidationException when {@code defaultValue} is
     *                                   {@code null}, or an element is
     *                                   {@code null} or not finite
     * @throws ConfigException          when the key is declared twice
     * @throws IllegalStateException    when the spec has already been built
     */
    public ConfigValue<List<Double>> doubleListValue(String key, List<Double> defaultValue,
                                                     String description)
    {
        beginValue(key);
        requireDescription(description);
        return register(new ConfigValue<List<Double>>(modId, key, description,
                ConfigType.DOUBLE_LIST, defaultValue, null, null, null, null, null));
    }

    /**
     * Declares a list of finite decimals with inclusive size bounds.
     *
     * @param key          the dotted key, not {@code null}
     * @param defaultValue the default elements, not {@code null}, finite, and
     *                     within the size bounds
     * @param minSize      the inclusive minimum size, at least {@code 0}
     * @param maxSize      the inclusive maximum size, at least {@code minSize}
     * @param description  the description, not {@code null}, possibly empty
     * @return the live value handle
     * @throws IllegalArgumentException when the key is malformed or the size
     *                                  bounds are invalid
     * @throws ConfigValidationException when {@code defaultValue} is
     *                                   {@code null}, the default size is
     *                                   outside the bounds, an element is
     *                                   {@code null}, or an element is not
     *                                   finite
     * @throws ConfigException          when the key is declared twice
     * @throws IllegalStateException    when the spec has already been built
     */
    public ConfigValue<List<Double>> doubleListValue(String key, List<Double> defaultValue,
                                                     int minSize, int maxSize, String description)
    {
        beginValue(key);
        requireDescription(description);
        requireSizeRange(key, minSize, maxSize, "size");
        return register(new ConfigValue<List<Double>>(modId, key, description,
                ConfigType.DOUBLE_LIST, defaultValue, null, null, minSize, maxSize, null));
    }

    /**
     * Declares an unbounded list of enum constants.
     *
     * @param key          the dotted key, not {@code null}
     * @param enumType     the enum class, not {@code null}
     * @param defaultValue the default constants, not {@code null} and all
     *                     members of {@code enumType}
     * @param description  the description, not {@code null}, possibly empty
     * @param <E>          the enum type
     * @return the live value handle
     * @throws NullPointerException     when {@code key}, {@code enumType}, or
     *                                  {@code description} is {@code null}
     * @throws IllegalArgumentException when the key is malformed or
     *                                  {@code enumType} is not an enum
     * @throws ConfigValidationException when {@code defaultValue} is
     *                                   {@code null}, or an element is
     *                                   {@code null} or not a member of
     *                                   {@code enumType}
     * @throws ConfigException          when the key is declared twice
     * @throws IllegalStateException    when the spec has already been built
     */
    public <E extends Enum<E>> ConfigValue<List<E>> enumListValue(String key, Class<E> enumType,
                                                                  List<E> defaultValue,
                                                                  String description)
    {
        beginValue(key);
        Objects.requireNonNull(enumType, "enumType");
        requireDescription(description);
        requireEnumType(key, enumType);
        return register(new ConfigValue<List<E>>(modId, key, description, ConfigType.ENUM_LIST,
                defaultValue, null, null, null, null, enumType));
    }

    /**
     * Declares a list of enum constants with inclusive size bounds.
     *
     * @param key          the dotted key, not {@code null}
     * @param enumType     the enum class, not {@code null}
     * @param defaultValue the default constants, not {@code null}, all members
     *                     of {@code enumType}, and within the size bounds
     * @param minSize      the inclusive minimum size, at least {@code 0}
     * @param maxSize      the inclusive maximum size, at least {@code minSize}
     * @param description  the description, not {@code null}, possibly empty
     * @param <E>          the enum type
     * @return the live value handle
     * @throws NullPointerException     when {@code key}, {@code enumType}, or
     *                                  {@code description} is {@code null}
     * @throws IllegalArgumentException when the key is malformed,
     *                                  {@code enumType} is not an enum, or the
     *                                  size bounds are invalid
     * @throws ConfigValidationException when {@code defaultValue} is
     *                                   {@code null}, the default size is
     *                                   outside the bounds, or an element is
     *                                   {@code null} or not a member of
     *                                   {@code enumType}
     * @throws ConfigException          when the key is declared twice
     * @throws IllegalStateException    when the spec has already been built
     */
    public <E extends Enum<E>> ConfigValue<List<E>> enumListValue(String key, Class<E> enumType,
                                                                  List<E> defaultValue,
                                                                  int minSize, int maxSize,
                                                                  String description)
    {
        beginValue(key);
        Objects.requireNonNull(enumType, "enumType");
        requireDescription(description);
        requireEnumType(key, enumType);
        requireSizeRange(key, minSize, maxSize, "size");
        return register(new ConfigValue<List<E>>(modId, key, description, ConfigType.ENUM_LIST,
                defaultValue, null, null, minSize, maxSize, enumType));
    }

    /**
     * Builds and returns the immutable spec.
     *
     * <p>The first successful call snapshots the current declarations. Later
     * calls return the same instance without rebuilding.
     *
     * @return the built spec, never {@code null}
     * @throws ConfigException when two migrations use the same
     *                         {@code fromVersion}
     */
    public ConfigSpec build()
    {
        if (spec != null)
        {
            return spec;
        }
        Set<Integer> seen = new HashSet<>();
        for (ConfigMigration migration : migrations)
        {
            if (!seen.add(migration.fromVersion()))
            {
                throw new ConfigException("Duplicate config migration fromVersion "
                        + migration.fromVersion() + " for mod '" + modId + "'");
            }
        }
        ConfigSpec builtSpec = new ConfigSpec(modId, side, schemaVersion, description,
                new ArrayList<>(values.values()), new ArrayList<>(migrations), syncPolicy);
        spec = builtSpec;
        built = true;
        return builtSpec;
    }

    private void ensureMutable()
    {
        if (built)
        {
            throw new IllegalStateException("ConfigBuilder for mod '" + modId
                    + "' has already built its spec");
        }
    }

    private void beginValue(String key)
    {
        ensureMutable();
        validateKey(key);
        requireNewKey(key);
    }

    private void validateKey(String key)
    {
        Objects.requireNonNull(key, "key");
        if (!KEY.matcher(key).matches())
        {
            throw new IllegalArgumentException("Invalid config key '" + key
                    + "': expected [A-Za-z0-9_-]+ segments separated by '.'");
        }
    }

    private void requireNewKey(String key)
    {
        if (values.containsKey(key))
        {
            throw new ConfigException("Duplicate config key '" + key + "' for mod '" + modId
                    + "'");
        }
    }

    private void requireDescription(String description)
    {
        Objects.requireNonNull(description, "description");
    }

    private void requireRange(String key, int min, int max)
    {
        if (min > max)
        {
            throw new IllegalArgumentException("Invalid config bounds for key '" + key
                    + "': min " + min + " is greater than max " + max);
        }
    }

    private void requireRange(String key, long min, long max)
    {
        if (min > max)
        {
            throw new IllegalArgumentException("Invalid config bounds for key '" + key
                    + "': min " + min + " is greater than max " + max);
        }
    }

    private void requireRange(String key, double min, double max)
    {
        if (!Double.isFinite(min) || !Double.isFinite(max))
        {
            throw new IllegalArgumentException("Invalid config bounds for key '" + key
                    + "': both bounds must be finite but were [" + min + ", " + max + "]");
        }
        if (min > max)
        {
            throw new IllegalArgumentException("Invalid config bounds for key '" + key
                    + "': min " + min + " is greater than max " + max);
        }
    }

    private void requireSizeRange(String key, int minSize, int maxSize, String label)
    {
        if (minSize < 0)
        {
            throw new IllegalArgumentException("Invalid config " + label + " bounds for key '"
                    + key + "': minimum must be >= 0 but was " + minSize);
        }
        if (maxSize < minSize)
        {
            throw new IllegalArgumentException("Invalid config " + label + " bounds for key '"
                    + key + "': max " + maxSize + " is less than min " + minSize);
        }
    }

    private void requireEnumType(String key, Class<?> enumType)
    {
        if (!enumType.isEnum())
        {
            throw new IllegalArgumentException("Invalid config key '" + key + "': "
                    + enumType.getName() + " is not an enum type");
        }
    }

    private <T> ConfigValue<T> register(ConfigValue<T> value)
    {
        values.put(value.key(), value);
        return value;
    }
}
