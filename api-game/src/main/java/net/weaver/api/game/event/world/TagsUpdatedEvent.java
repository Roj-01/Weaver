package net.weaver.api.game.event.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.server.ReloadableServerResources;
import net.weaver.api.event.Event;

/**
 * Fired on the server after datapack tags were bound to the registries
 * ({@code ReloadableServerResources#updateComponentsAndStaticRegistryTags}):
 * at startup and after every {@code /reload}. Tag lookups are up to date
 * from here on; rebuild any caches derived from tags.
 */
public final class TagsUpdatedEvent extends Event {
    private final ReloadableServerResources resources;

    public TagsUpdatedEvent(ReloadableServerResources resources) {
        this.resources = resources;
    }

    public ReloadableServerResources resources() {
        return resources;
    }

    /** The full registry lookup, including datapack registries. */
    public HolderLookup.Provider registries() {
        return resources.fullRegistries().lookup();
    }
}
