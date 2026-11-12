package net.weaver.api.game.event.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.weaver.api.event.Event;

/**
 * Fired on the server each time data packs load (startup and {@code /reload})
 * when the reload collects its listeners ({@code ReloadableServerResources#listeners}).
 * Add a listener, for example a {@code SimpleJsonResourceReloadListener}, to
 * load mod data from data packs. Added listeners run after vanilla's; register
 * the event listener during mod setup.
 */
public final class AddServerReloadListenersEvent extends Event {
    private final ReloadableServerResources resources;
    private final List<PreparableReloadListener> added = new ArrayList<>();

    public AddServerReloadListenersEvent(ReloadableServerResources resources) {
        this.resources = resources;
    }

    /** The resources being loaded: recipes, advancements, functions, tags, loot. */
    public ReloadableServerResources resources() {
        return resources;
    }

    public void addListener(PreparableReloadListener listener) {
        added.add(Objects.requireNonNull(listener, "listener"));
    }

    /** The listeners added so far. */
    public List<PreparableReloadListener> added() {
        return List.copyOf(added);
    }
}
