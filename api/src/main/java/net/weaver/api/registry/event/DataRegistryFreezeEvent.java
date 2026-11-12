package net.weaver.api.registry.event;

import net.weaver.api.event.Event;
import net.weaver.api.registry.DataRegistry;
import net.weaver.api.registry.RegistryKey;

import java.util.Objects;

/**
 * Posted on the game bus after a data-driven registry freeze was attempted.
 *
 * <p>This event is only for {@link DataRegistry} instances managed by
 * {@code DataRegistryManager}. Game registries keep using
 * {@link net.weaver.api.event.events.RegisterEvent} and
 * {@link net.weaver.api.event.events.RegistryFreezeEvent}.</p>
 *
 * <p>Freezing is best-effort. Check {@link #frozen()} or
 * {@link #registry()}{@code .isFrozen()} instead of assuming every announced
 * registry is closed. The event is not cancelable.</p>
 */
public final class DataRegistryFreezeEvent extends Event
{
    private final DataRegistry<?> registry;

    /**
     * Creates an event for one data registry.
     *
     * @param registry the registry whose freeze was attempted, not
     *                 {@code null}
     */
    public DataRegistryFreezeEvent(DataRegistry<?> registry)
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
     * Whether the registry is frozen at the time of this event.
     *
     * @return {@code true} when the freeze took effect
     */
    public boolean frozen()
    {
        return registry.isFrozen();
    }
}
