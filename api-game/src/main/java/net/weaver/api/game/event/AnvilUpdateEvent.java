package net.weaver.api.game.event;

import net.minecraft.world.inventory.AnvilMenu;

/**
 * Typed variant of {@link net.weaver.api.event.events.AnvilUpdateEvent}.
 *
 * <p>Fired on the server thread at method entry, before
 * {@code AnvilMenu#createResult} recomputes the result. Observation-only and
 * not cancelable. The payload is narrowed to the game type. The inherited
 * {@link Object}-typed accessor stays available for binary compatibility.
 */
public final class AnvilUpdateEvent extends net.weaver.api.event.events.AnvilUpdateEvent {

    /**
     * Creates a typed anvil-update event.
     *
     * @param menu the anvil menu recomputing its result
     */
    public AnvilUpdateEvent(Object menu) {
        super(menu);
    }

    @Override
    public AnvilMenu menu() {
        return (AnvilMenu) super.menu();
    }
}
