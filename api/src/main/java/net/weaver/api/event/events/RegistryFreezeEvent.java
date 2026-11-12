package net.weaver.api.event.events;

import net.weaver.api.event.Event;
import net.weaver.api.registry.RegistryView;
import net.weaver.api.registry.RegistryKey;
import net.weaver.api.registry.RegistryManager;

import java.util.Objects;

public final class RegistryFreezeEvent extends Event
{
    private final RegistryKey<?> registryKey;

    public RegistryFreezeEvent(RegistryKey<?> registryKey)
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
}
