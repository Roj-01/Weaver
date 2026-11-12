package net.weaver.api.registry;

import java.util.Objects;

/**
 * An immutable typed key for one entry in a {@link DataRegistry}.
 *
 * <p>A data registry key combines the registry key, the entry key, and the
 * entry type used by the registry codec. The type participates in equality, so
 * two keys for the same registry and entry id but with different types are not
 * equal.</p>
 *
 * <p>{@link #toString()} returns {@code registry.id() + "#" + id.id()}, for
 * example {@code example:gems#example:ruby}.</p>
 *
 * @param <T> the entry type
 */
public final class DataRegistryKey<T> implements Comparable<DataRegistryKey<T>>
{
    private final RegistryKey<?> registry;
    private final RegistryKey<T> id;
    private final Class<T> type;

    private DataRegistryKey(RegistryKey<?> registry, RegistryKey<T> id, Class<T> type)
    {
        this.registry = registry;
        this.id = id;
        this.type = type;
    }

    /**
     * Creates a key from a namespace and path.
     *
     * @param registry the registry that owns the entry, not {@code null}
     * @param namespace the entry namespace, not {@code null}
     * @param path the entry path, not {@code null}
     * @param type the entry type, not {@code null}
     * @param <T> the entry type
     * @return a new key
     * @throws IllegalArgumentException when the namespace or path is invalid
     */
    public static <T> DataRegistryKey<T> of(RegistryKey<?> registry, String namespace, String path,
                                            Class<T> type)
    {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(type, "type");
        return new DataRegistryKey<>(registry, RegistryKey.of(namespace, path), type);
    }

    /**
     * Creates a key from a full {@code namespace:path} id.
     *
     * <p>A path without a namespace defaults to {@code minecraft}, matching
     * {@link RegistryKey#parse(String)}.</p>
     *
     * @param registry the registry that owns the entry, not {@code null}
     * @param id the full entry id, not {@code null}
     * @param type the entry type, not {@code null}
     * @param <T> the entry type
     * @return a new key
     * @throws IllegalArgumentException when the id is invalid
     */
    public static <T> DataRegistryKey<T> of(RegistryKey<?> registry, String id, Class<T> type)
    {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        return new DataRegistryKey<>(registry, RegistryKey.parse(id), type);
    }

    /**
     * The registry that owns the entry.
     *
     * @return the registry key, never {@code null}
     */
    public RegistryKey<?> registry()
    {
        return registry;
    }

    /**
     * The entry id inside the registry.
     *
     * @return the entry key, never {@code null}
     */
    public RegistryKey<T> id()
    {
        return id;
    }

    /**
     * The entry namespace.
     *
     * @return the namespace, never {@code null}
     */
    public String namespace()
    {
        return id.namespace();
    }

    /**
     * The entry path.
     *
     * @return the path, never {@code null}
     */
    public String path()
    {
        return id.path();
    }

    /**
     * The entry type checked by {@link #cast(Object)}.
     *
     * @return the entry type, never {@code null}
     */
    public Class<T> type()
    {
        return type;
    }

    /**
     * Casts a value to this key's entry type.
     *
     * @param value the value to cast
     * @return the value cast to {@link #type()}
     * @throws NullPointerException when {@code value} is {@code null}
     * @throws ClassCastException when the value is not an instance of the type
     */
    public T cast(Object value)
    {
        return type.cast(value);
    }

    /**
     * Orders keys by entry id only. The registry and type do not participate,
     * so keys from different registries can still be sorted together.
     *
     * @param other the key to compare with
     * @return the comparison result of the entry ids
     */
    @Override
    public int compareTo(DataRegistryKey<T> other)
    {
        final int registryOrder = registry.id().compareTo(other.registry.id());
        if (registryOrder != 0)
        {
            return registryOrder;
        }
        final int idOrder = id.compareTo(other.id);
        if (idOrder != 0)
        {
            return idOrder;
        }
        return type.getName().compareTo(other.type.getName());
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof DataRegistryKey<?> key))
        {
            return false;
        }
        return registry.equals(key.registry) && id.equals(key.id) && type == key.type;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(registry, id, type);
    }

    @Override
    public String toString()
    {
        return registry.id() + "#" + id.id();
    }
}
