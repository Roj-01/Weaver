package net.weaver.api.game.event.client;

import net.weaver.api.event.Event;

/**
 * Fired on the client when the mouse wheel turns while no screen is open
 * ({@code MouseHandler#onScroll}), before vanilla scrolls the hotbar or the
 * spectator menu. Cancelable: a canceled event skips that.
 */
public final class ClientMouseScrollEvent extends Event {
    private final double deltaX;
    private final double deltaY;

    public ClientMouseScrollEvent(double deltaX, double deltaY) {
        super(true);
        this.deltaX = deltaX;
        this.deltaY = deltaY;
    }

    /** Raw horizontal scroll amount. */
    public double deltaX() {
        return deltaX;
    }

    /** Raw vertical scroll amount; positive is away from the user. */
    public double deltaY() {
        return deltaY;
    }
}
