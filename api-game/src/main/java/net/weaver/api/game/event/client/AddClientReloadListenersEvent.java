package net.weaver.api.game.event.client;

import java.util.Objects;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.weaver.api.event.Event;

/**
 * Fired once on the client while the game starts, after vanilla registered
 * its resource reload listeners and before the first resource load
 * ({@code Minecraft}'s constructor, before {@code createReload}). Add a
 * listener to load mod assets from resource packs; it runs on every later
 * resource reload too. Register the event listener during mod setup.
 */
public final class AddClientReloadListenersEvent extends Event {
    private final ReloadableResourceManager resourceManager;

    public AddClientReloadListenersEvent(ReloadableResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    public ReloadableResourceManager resourceManager() {
        return resourceManager;
    }

    public void addListener(PreparableReloadListener listener) {
        resourceManager.registerReloadListener(Objects.requireNonNull(listener, "listener"));
    }
}
