package net.weaver.api.event.events;

import net.weaver.api.event.Event;
import net.weaver.api.registry.RegistryView;
import net.weaver.api.registry.RegistryEntry;
import net.weaver.api.registry.RegistryKey;
import net.weaver.api.registry.RegistryManager;

import java.util.Objects;
import java.util.function.Supplier;

public final class RegisterEvent extends Event
{
    private final RegistryKey<?> registryKey;

    public RegisterEvent(RegistryKey<?> registryKey)
    {
        this.registryKey = Objects.requireNonNull(registryKey, "registryKey");
    }

    public RegistryKey<?> registryKey()
    {
        return registryKey;
    }

    public String registryId()
    {
        return registryKey.id();
    }

    @SuppressWarnings("unchecked")
    public <T> RegistryView<T> registry()
    {
        return RegistryManager.registry(registryKey);
    }

    /**
     * Returns the live view for a registry, provided it is the registry this
     * event describes.
     *
     * <p>The typed parameter lets callers keep the element type from a typed
     * registry key. Passing a key for a different registry is a programming
     * error and is rejected before the registry backend is touched.
     *
     * @param key the registry key to open, not {@code null}
     * @param <T> the registry element type
     * @return the live view for {@code key}
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} is not this event's
     *                                  registry
     * @throws net.weaver.api.registry.RegistryException if the registry
     *                                  backend is unavailable or does not know
     *                                  the registry
     */
    public <T> RegistryView<T> registry(RegistryKey<T> key)
    {
        Objects.requireNonNull(key, "key");
        if (!key.equals(registryKey))
        {
            throw new IllegalArgumentException("Registry key " + key.id()
                    + " does not match event registry " + registryKey.id());
        }
        return RegistryManager.registry(key);
    }

    public boolean isFrozen()
    {
        return registry().isFrozen();
    }

    public <T> RegistryEntry<T> register(RegistryKey<T> key, Supplier<? extends T> value)
    {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        final RegistryView<T> registry = registry();
        // Check for a duplicate before running the supplier: constructing a Block or Item
        // creates an intrusive holder the game requires to be registered before freezing.
        if (registry.containsKey(key))
        {
            throw new net.weaver.api.registry.RegistryException("Duplicate registry key: " + key.id());
        }
        final T supplied = Objects.requireNonNull(value.get(), "value");
        return registry.register(key, supplied);
    }

    public <T> RegistryEntry<T> register(String namespace, String path, Supplier<? extends T> value)
    {
        Objects.requireNonNull(value, "value");
        return register(RegistryKey.of(namespace, path), value);
    }

    public <T> RegistryEntry<T> register(String id, Supplier<? extends T> value)
    {
        Objects.requireNonNull(value, "value");
        return register(RegistryKey.parse(id), value);
    }

    @SuppressWarnings("unchecked")
    public void registerRaw(String id, Object value)
    {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(value, "value");
        final RegistryView<Object> registry = (RegistryView<Object>) (RegistryView<?>) registry();
        registry.register(RegistryKey.parse(id), value);
    }
}
