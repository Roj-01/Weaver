package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A server player completed a container-menu close.
 *
 * <p>Fired at method entry to {@code ServerPlayer#doCloseContainer}, before the
 * container menu is removed and the player's inventory menu is restored. This
 * is the completion path, distinct from {@link ContainerCloseEvent}, which fires
 * earlier at {@code ServerPlayer#closeContainer} entry. The event is posted
 * only when a non-inventory menu is open. Observation-only and not cancelable.
 *
 * <p>The base payloads are game objects carried as {@link Object}; the typed
 * {@code api-game} event exposes game types.
 */
public class ContainerClosedEvent extends Event {

    private final Object player;
    private final Object containerMenu;

    /**
     * Creates a container-closed event.
     *
     * @param player        the player whose container menu is closing
     * @param containerMenu the container menu being closed
     */
    public ContainerClosedEvent(Object player, Object containerMenu) {
        this.player = Objects.requireNonNull(player, "player");
        this.containerMenu = Objects.requireNonNull(containerMenu, "containerMenu");
    }

    /** The player whose container menu is closing. */
    public Object player() {
        return player;
    }

    /** The container menu being closed. */
    public Object containerMenu() {
        return containerMenu;
    }
}
