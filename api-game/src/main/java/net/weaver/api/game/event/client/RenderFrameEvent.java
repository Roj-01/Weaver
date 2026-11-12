package net.weaver.api.game.event.client;

import java.util.Objects;
import net.minecraft.client.DeltaTracker;
import net.weaver.api.event.Event;

/**
 * Fired on the render thread around the drawing of each frame
 * ({@code GameRenderer#render} in {@code Minecraft#renderFrame}): {@code PRE}
 * before it, {@code POST} after it. Fires every frame; keep listeners cheap.
 */
public final class RenderFrameEvent extends Event {
    /** Before or after the frame is drawn. */
    public enum Phase {
        PRE,
        POST
    }

    private final Phase phase;
    private final DeltaTracker deltaTracker;

    public RenderFrameEvent(Phase phase, DeltaTracker deltaTracker) {
        this.phase = Objects.requireNonNull(phase, "phase");
        this.deltaTracker = deltaTracker;
    }

    public Phase phase() {
        return phase;
    }

    public DeltaTracker deltaTracker() {
        return deltaTracker;
    }
}
