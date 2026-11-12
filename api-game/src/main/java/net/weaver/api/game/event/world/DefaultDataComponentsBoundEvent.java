package net.weaver.api.game.event.world;

import net.minecraft.core.HolderLookup;
import net.weaver.api.event.Event;

/**
 * Fired after the default data components of items (and other component
 * holders) have been bound to the current registries: on the server after each
 * data pack load or reload ({@code ReloadableServerResources}), and on a client
 * after it received the server registries ({@code RegistryDataCollector}).
 * From here on, {@code Item#components()} reflects data-driven values such as
 * enchantment and tag lookups. The client post runs on the network thread.
 */
public final class DefaultDataComponentsBoundEvent extends Event {
    private final HolderLookup.Provider registries;
    private final boolean client;

    public DefaultDataComponentsBoundEvent(HolderLookup.Provider registries, boolean client) {
        this.registries = registries;
        this.client = client;
    }

    public HolderLookup.Provider registries() {
        return registries;
    }

    /** True when fired on a client that received registries from a server. */
    public boolean client() {
        return client;
    }
}
