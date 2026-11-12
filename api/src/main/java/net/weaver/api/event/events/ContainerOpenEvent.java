package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A server player is opening a menu. Observation-only; the base payloads are
 * game objects carried as {@link Object}.
 */
public class ContainerOpenEvent extends Event {

    private final Object player;
    private final Object menuProvider;

    /**
     * Creates a container-open event.
     *
     * @param player       the player opening the menu
     * @param menuProvider the provider that builds the menu
     */
    public ContainerOpenEvent(Object player, Object menuProvider) {
        this.player = Objects.requireNonNull(player, "player");
        this.menuProvider = Objects.requireNonNull(menuProvider, "menuProvider");
    }

    /** The player opening the menu. */
    public Object player() {
        return player;
    }

    /** The menu provider. */
    public Object menuProvider() {
        return menuProvider;
    }
}
