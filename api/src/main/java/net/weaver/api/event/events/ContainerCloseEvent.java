package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A server player is about to close a container menu.
 *
 * <p>Fired on the server thread at method entry, before
 * {@code ServerPlayer#closeContainer}. {@code menu} is the
 * {@code AbstractContainerMenu} being closed. The event is not emitted when no
 * non-inventory menu is open. Observation-only and not cancelable. The base
 * payloads are game objects carried as {@link Object}; the typed
 * {@code api-game} event exposes game types.
 */
public class ContainerCloseEvent extends Event {

    private final Object player;
    private final Object menu;

    /**
     * Creates a container-close event.
     *
     * @param player the player closing the menu
     * @param menu   the menu being closed
     */
    public ContainerCloseEvent(Object player, Object menu) {
        this.player = Objects.requireNonNull(player, "player");
        this.menu = Objects.requireNonNull(menu, "menu");
    }

    /** The player closing the menu. */
    public Object player() {
        return player;
    }

    /** The menu being closed. */
    public Object menu() {
        return menu;
    }
}
