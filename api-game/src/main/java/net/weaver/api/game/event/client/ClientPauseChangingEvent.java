package net.weaver.api.game.event.client;

import net.weaver.api.event.Event;

/**
 * Fired on the client just before a single-player game pauses or resumes
 * ({@code Minecraft#runTick}, when a pausing screen opened or closed).
 * Cancelable: a canceled change keeps the game in its current state (a mod can
 * keep the world running behind its own pausing screen) until the screen that
 * asked for it goes away; the event is not posted again every frame meanwhile.
 * After an uncanceled change {@link ClientPauseChangedEvent} follows.
 */
public final class ClientPauseChangingEvent extends Event {
    private final boolean paused;

    public ClientPauseChangingEvent(boolean paused) {
        super(true);
        this.paused = paused;
    }

    /** Whether the game is about to pause (true) or resume (false). */
    public boolean paused() {
        return paused;
    }
}
