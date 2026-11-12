package net.weaver.api.registry.event;

import net.weaver.api.event.Event;
import net.weaver.api.registry.DataRegistry;
import net.weaver.api.registry.DataRegistryKey;
import net.weaver.api.registry.RegistryEntry;
import net.weaver.api.registry.RegistryKey;

import java.util.Objects;

/**
 * Posted on the game bus when a data-driven registry registration phase opens.
 *
 * <p>This event is only for {@link DataRegistry} instances managed by
 * {@code DataRegistryManager}. Game registries keep using
 * {@link net.weaver.api.event.events.RegisterEvent} and
 * {@link net.weaver.api.event.events.RegistryFreezeEvent}.</p>
 *
 * <p>The event is not cancelable.</p>
 */
public final class DataRegistryRegisterEvent extends Event
{
    private final DataRegistry<?> registry;

    /**
     * Creates an event for one data registry.
     *
     * @param registry the registry that is open for registration, not
     *                 {@code null}
     */
    public DataRegistryRegisterEvent(DataRegistry<?> registry)
    {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * The registry key.
     *
     * @return the registry key, never {@code null}
     */
    public RegistryKey<?> registryKey()
    {
        return registry.registryKey();
    }

    /**
     * The registry id.
     *
     * @return the full {@code namespace:path} registry id
     */
    public String registryId()
    {
        return registry.id();
    }

    /**
     * The data registry this event describes.
     *
     * @return the registry, never {@code null}
     */
    public DataRegistry<?> registry()
    {
        return registry;
    }

    /**
     * Whether the registry is already frozen.
     *
     * @return {@code true} when the registry rejects writes
     */
    public boolean isFrozen()
    {
        return registry.isFrozen();
    }

    /**
     * Registers one programmatic entry.
     *
     * @param key the entry key, not {@code null} and in this event's registry
     * @param value the entry value, not {@code null}
     * @param <T> the entry type
     * @return the bound entry
     * @throws IllegalArgumentException when the key belongs to another registry
     *                                  or names another type
     * @throws net.weaver.api.registry.RegistryException when the registry is
     *                                  frozen or the id already exists
     */
    @SuppressWarnings("unchecked")
    public <T> RegistryEntry<T> register(DataRegistryKey<T> key, T value)
    {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (!key.registry().equals(registry.registryKey()))
        {
            throw new IllegalArgumentException("Data registry key " + key
                    + " does not match event registry " + registry.id());
        }
        return ((DataRegistry<T>) registry).register(key, value);
    }

    /**
     * Registers one programmatic entry by path.
     *
     * <p>The value is cast to {@link DataRegistry#type()} before it reaches the
     * registry.</p>
     *
     * @param path the entry path or full id, not {@code null}
     * @param value the entry value, not {@code null}
     * @return the bound entry
     * @throws ClassCastException when the value is not of the registry type
     * @throws net.weaver.api.registry.RegistryException when the registry is
     *                                  frozen or the id already exists
     */
    @SuppressWarnings("unchecked")
    public RegistryEntry<?> register(String path, Object value)
    {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(value, "value");
        final Object cast = registry.type().cast(value);
        return ((DataRegistry<Object>) registry).register(path, cast);
    }
}
