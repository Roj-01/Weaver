package net.weaver.api.config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A mutable, JSON-shaped configuration document used during loading and
 * migration.
 *
 * <p>The constructor copies the supplied map and every nested list and map, so
 * later changes to this instance never alter the input map and vice versa.
 * {@link #values()} returns an immutable deep snapshot that preserves
 * insertion order.
 *
 * <p>Keys are plain strings; the manager's declared-key format is not imposed
 * here because raw files and migrations may temporarily use legacy names.
 * Stored values are expected to be JSON-compatible: {@link Boolean},
 * {@link String}, {@link Number}, {@link List}, or {@link Map}.
 */
public final class ConfigData
{
    private static final double LONG_MIN_AS_DOUBLE = -9.223372036854776E18;
    private static final double LONG_UPPER_EXCLUSIVE = 9.223372036854776E18;
    private static final int MAX_DESCRIBED_LENGTH = 80;

    private final int schemaVersion;
    private final LinkedHashMap<String, Object> values;

    /**
     * Creates a document with the given schema version and values.
     *
     * @param schemaVersion the schema version of the document
     * @param values        the raw values, not {@code null}; keys may not be
     *                      {@code null}
     * @throws NullPointerException when {@code values} or a top-level key is
     *                              {@code null}
     */
    public ConfigData(int schemaVersion, Map<String, Object> values)
    {
        this.schemaVersion = schemaVersion;
        this.values = copyJsonMap(Objects.requireNonNull(values, "values"), "values");
    }

    /**
     * The schema version of this document.
     *
     * @return the schema version
     */
    public int schemaVersion()
    {
        return schemaVersion;
    }

    /**
     * An immutable deep snapshot of the current values in insertion order.
     *
     * @return an immutable map, never {@code null}
     */
    public Map<String, Object> values()
    {
        return freezeJsonMap(values);
    }

    /**
     * The current keys in insertion order.
     *
     * @return an immutable set of keys, never {@code null}
     */
    public Set<String> keys()
    {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values.keySet()));
    }

    /**
     * The number of stored values.
     *
     * @return the value count
     */
    public int size()
    {
        return values.size();
    }

    /**
     * Whether the document contains a key.
     *
     * @param key the key to test, not {@code null}
     * @return {@code true} when the key is present, even with a {@code null}
     *         value
     */
    public boolean has(String key)
    {
        return values.containsKey(Objects.requireNonNull(key, "key"));
    }

    /**
     * Returns the raw value for a key.
     *
     * @param key the key to read, not {@code null}
     * @return the stored value, or {@code null} when the key is absent or its
     *         value is {@code null}
     */
    public Object get(String key)
    {
        return values.get(Objects.requireNonNull(key, "key"));
    }

    /**
     * Reads a string value.
     *
     * @param key      the key to read, not {@code null}
     * @param fallback the value returned when the key is absent
     * @return the string value, or {@code fallback} when the key is absent
     * @throws ConfigException when the key is present but its value is not a
     *                         {@link String}
     */
    public String getString(String key, String fallback)
    {
        Objects.requireNonNull(key, "key");
        if (!values.containsKey(key))
        {
            return fallback;
        }
        Object value = values.get(key);
        if (value instanceof String text)
        {
            return text;
        }
        throw shapeError(key, "a string", value);
    }

    /**
     * Reads an integer value.
     *
     * @param key      the key to read, not {@code null}
     * @param fallback the value returned when the key is absent
     * @return the integer value, or {@code fallback} when the key is absent
     * @throws ConfigException when the key is present but its value is not an
     *                         integral number in {@code int} range
     */
    public int getInt(String key, int fallback)
    {
        Objects.requireNonNull(key, "key");
        if (!values.containsKey(key))
        {
            return fallback;
        }
        Object value = values.get(key);
        long number = integralLong(key, value);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE)
        {
            throw shapeError(key, "an integer in range", value);
        }
        return (int) number;
    }

    /**
     * Reads a long value.
     *
     * @param key      the key to read, not {@code null}
     * @param fallback the value returned when the key is absent
     * @return the long value, or {@code fallback} when the key is absent
     * @throws ConfigException when the key is present but its value is not an
     *                         integral number in {@code long} range
     */
    public long getLong(String key, long fallback)
    {
        Objects.requireNonNull(key, "key");
        if (!values.containsKey(key))
        {
            return fallback;
        }
        return integralLong(key, values.get(key));
    }

    /**
     * Reads a decimal value.
     *
     * @param key      the key to read, not {@code null}
     * @param fallback the value returned when the key is absent
     * @return the double value, or {@code fallback} when the key is absent
     * @throws ConfigException when the key is present but its value is not a
     *                         {@link Number}
     */
    public double getDouble(String key, double fallback)
    {
        Objects.requireNonNull(key, "key");
        if (!values.containsKey(key))
        {
            return fallback;
        }
        Object value = values.get(key);
        if (value instanceof Number number)
        {
            return number.doubleValue();
        }
        throw shapeError(key, "a number", value);
    }

    /**
     * Reads a boolean value.
     *
     * @param key      the key to read, not {@code null}
     * @param fallback the value returned when the key is absent
     * @return the boolean value, or {@code fallback} when the key is absent
     * @throws ConfigException when the key is present but its value is not a
     *                         {@link Boolean}
     */
    public boolean getBoolean(String key, boolean fallback)
    {
        Objects.requireNonNull(key, "key");
        if (!values.containsKey(key))
        {
            return fallback;
        }
        Object value = values.get(key);
        if (value instanceof Boolean flag)
        {
            return flag;
        }
        throw shapeError(key, "a boolean", value);
    }

    /**
     * Reads a list value.
     *
     * <p>The returned list is the live internal list; migrations may mutate it
     * in place. Use {@link #values()} for an immutable snapshot.
     *
     * @param key the key to read, not {@code null}
     * @return the stored list, or an empty immutable list when the key is
     *         absent
     * @throws ConfigException when the key is present but its value is not a
     *                         {@link List}
     */
    @SuppressWarnings("unchecked")
    public List<Object> getList(String key)
    {
        Objects.requireNonNull(key, "key");
        if (!values.containsKey(key))
        {
            return List.of();
        }
        Object value = values.get(key);
        if (value instanceof List<?>)
        {
            return (List<Object>) value;
        }
        throw shapeError(key, "a list", value);
    }

    /**
     * Stores a raw value.
     *
     * @param key   the key to write, not {@code null}
     * @param value the value to store, not {@code null} and JSON-compatible
     * @throws NullPointerException     when {@code key} or {@code value} is
     *                                  {@code null}
     * @throws IllegalArgumentException when {@code value} or a nested element
     *                                  is not JSON-compatible
     */
    public void put(String key, Object value)
    {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        requireJsonCompatible(value, "Config data value for key '" + key + "'");
        values.put(key, value);
    }

    /**
     * Removes a key.
     *
     * @param key the key to remove, not {@code null}
     * @return the previous value, or {@code null} when the key was absent
     */
    public Object remove(String key)
    {
        return values.remove(Objects.requireNonNull(key, "key"));
    }

    /**
     * Renames a key while preserving its position in insertion order.
     *
     * <p>Renaming an absent key is a no-op. The target key must not already
     * exist.
     *
     * @param oldKey the existing key, not {@code null}
     * @param newKey the new key, not {@code null}
     * @throws ConfigException when {@code oldKey} exists and {@code newKey} is
     *                         already present
     */
    public void rename(String oldKey, String newKey)
    {
        Objects.requireNonNull(oldKey, "oldKey");
        Objects.requireNonNull(newKey, "newKey");
        if (!values.containsKey(oldKey))
        {
            return;
        }
        if (values.containsKey(newKey))
        {
            throw new ConfigException("Cannot rename config data key '" + oldKey + "' to '"
                    + newKey + "': the target key already exists");
        }
        LinkedHashMap<String, Object> renamed = new LinkedHashMap<>(values.size());
        for (Map.Entry<String, Object> entry : values.entrySet())
        {
            if (entry.getKey().equals(oldKey))
            {
                renamed.put(newKey, entry.getValue());
            }
            else
            {
                renamed.put(entry.getKey(), entry.getValue());
            }
        }
        values.clear();
        values.putAll(renamed);
    }

    static LinkedHashMap<String, Object> copyJsonMap(Map<String, Object> source, String name)
    {
        Objects.requireNonNull(source, name);
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>(source.size());
        for (Map.Entry<String, Object> entry : source.entrySet())
        {
            copy.put(Objects.requireNonNull(entry.getKey(), name + " key"),
                    copyJsonValue(entry.getValue()));
        }
        return copy;
    }

    static Map<String, Object> freezeJsonMap(Map<String, Object> source)
    {
        Objects.requireNonNull(source, "values");
        LinkedHashMap<String, Object> frozen = new LinkedHashMap<>(source.size());
        for (Map.Entry<String, Object> entry : source.entrySet())
        {
            frozen.put(Objects.requireNonNull(entry.getKey(), "values key"),
                    freezeJsonValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    private static Object copyJsonValue(Object value)
    {
        if (value instanceof List<?> list)
        {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list)
            {
                copy.add(copyJsonValue(element));
            }
            return copy;
        }
        if (value instanceof Map<?, ?> map)
        {
            LinkedHashMap<Object, Object> copy = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet())
            {
                copy.put(entry.getKey(), copyJsonValue(entry.getValue()));
            }
            return copy;
        }
        return value;
    }

    private static Object freezeJsonValue(Object value)
    {
        if (value instanceof List<?> list)
        {
            List<Object> frozen = new ArrayList<>(list.size());
            for (Object element : list)
            {
                frozen.add(freezeJsonValue(element));
            }
            return Collections.unmodifiableList(frozen);
        }
        if (value instanceof Map<?, ?> map)
        {
            LinkedHashMap<Object, Object> frozen = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet())
            {
                frozen.put(entry.getKey(), freezeJsonValue(entry.getValue()));
            }
            return Collections.unmodifiableMap(frozen);
        }
        return value;
    }

    private static void requireJsonCompatible(Object value, String description)
    {
        if (value == null || value instanceof Boolean || value instanceof String
                || value instanceof Number)
        {
            return;
        }
        if (value instanceof List<?> list)
        {
            for (Object element : list)
            {
                requireJsonCompatible(element, description);
            }
            return;
        }
        if (value instanceof Map<?, ?> map)
        {
            for (Map.Entry<?, ?> entry : map.entrySet())
            {
                if (!(entry.getKey() instanceof String))
                {
                    throw new IllegalArgumentException(description
                            + " contains a non-string map key: " + entry.getKey());
                }
                requireJsonCompatible(entry.getValue(), description);
            }
            return;
        }
        throw new IllegalArgumentException(description
                + " must be JSON-compatible (Boolean, String, Number, List, or Map) but was "
                + value.getClass().getName());
    }

    private static long integralLong(String key, Object value)
    {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long)
        {
            return ((Number) value).longValue();
        }
        if (value instanceof BigInteger integer)
        {
            try
            {
                return integer.longValueExact();
            }
            catch (ArithmeticException failure)
            {
                throw shapeError(key, "an integral number in long range", value);
            }
        }
        if (value instanceof BigDecimal decimal)
        {
            try
            {
                return decimal.longValueExact();
            }
            catch (ArithmeticException failure)
            {
                throw shapeError(key, "an integral number in long range", value);
            }
        }
        if (value instanceof Number number)
        {
            double candidate = number.doubleValue();
            if (!Double.isFinite(candidate) || candidate != Math.rint(candidate))
            {
                throw shapeError(key, "an integral number", value);
            }
            if (candidate < LONG_MIN_AS_DOUBLE || candidate >= LONG_UPPER_EXCLUSIVE)
            {
                throw shapeError(key, "an integral number in long range", value);
            }
            return (long) candidate;
        }
        throw shapeError(key, "an integral number", value);
    }

    private static ConfigException shapeError(String key, String expected, Object actual)
    {
        return new ConfigException("Config data key '" + key + "' must be " + expected
                + " but was " + describe(actual));
    }

    private static String describe(Object value)
    {
        if (value == null)
        {
            return "null";
        }
        String text = value instanceof String string
                ? '"' + string + '"'
                : String.valueOf(value);
        return text.length() <= MAX_DESCRIBED_LENGTH
                ? text
                : text.substring(0, MAX_DESCRIBED_LENGTH - 3) + "...";
    }
}
