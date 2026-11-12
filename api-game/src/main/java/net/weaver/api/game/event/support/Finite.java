package net.weaver.api.game.event.support;

/**
 * Rejects NaN and infinite values set by event listeners. A rejected set throws
 * inside the listener, where the event bus isolates it, so the vanilla value
 * stays: a NaN health or field of view would otherwise persist in the game
 * (and in saves) with no way back.
 */
public final class Finite {
    private Finite() {
    }

    public static float check(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite, got " + value);
        }
        return value;
    }

    public static double check(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite, got " + value);
        }
        return value;
    }
}
