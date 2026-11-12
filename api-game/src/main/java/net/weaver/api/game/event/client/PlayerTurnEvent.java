package net.weaver.api.game.event.client;

import net.weaver.api.event.Event;

/**
 * Fired on the client when mouse movement is about to turn the local player
 * ({@code MouseHandler#turnPlayer}, the {@code LocalPlayer#turn} call), after
 * sensitivity, smoothing, and inversion were applied. Change the deltas to
 * slow, speed up, or lock the camera; {@code 0} for both stops turning.
 */
public final class PlayerTurnEvent extends Event {
    private double yawDelta;
    private double pitchDelta;

    public PlayerTurnEvent(double yawDelta, double pitchDelta) {
        this.yawDelta = yawDelta;
        this.pitchDelta = pitchDelta;
    }

    public double yawDelta() {
        return yawDelta;
    }

    public void setYawDelta(double yawDelta) {
        net.weaver.api.game.event.support.Finite.check(yawDelta, "yawDelta");
        this.yawDelta = yawDelta;
    }

    public double pitchDelta() {
        return pitchDelta;
    }

    public void setPitchDelta(double pitchDelta) {
        net.weaver.api.game.event.support.Finite.check(pitchDelta, "pitchDelta");
        this.pitchDelta = pitchDelta;
    }
}
