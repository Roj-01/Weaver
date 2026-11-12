package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * An anvil menu is about to recompute its result.
 *
 * <p>Fired on the server thread at method entry, before
 * {@code AnvilMenu#createResult} recomputes the result. Observation-only and
 * not cancelable. The base payload is a game object carried as {@link Object};
 * the typed {@code api-game} event exposes game types.
 */
public class AnvilUpdateEvent extends Event {

    private final Object menu;

    /**
     * Creates an anvil-update event.
     *
     * @param menu the anvil menu recomputing its result
     */
    public AnvilUpdateEvent(Object menu) {
        this.menu = Objects.requireNonNull(menu, "menu");
    }

    /** The anvil menu recomputing its result. */
    public Object menu() {
        return menu;
    }
}
