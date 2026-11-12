package net.weaver.api.game.event.client;

import net.weaver.api.event.Event;

/**
 * Fired on the client after a single-player game paused or resumed
 * ({@code Minecraft#runTick}, when the pause flag changed): a pausing screen
 * opened or closed. Not cancelable.
 */
public final class ClientPauseChangedEvent extends Event {
    private final boolean paused;

    public ClientPauseChangedEvent(boolean paused) {
        this.paused = paused;
    }

    public boolean paused() {
        return paused;
    }
}
