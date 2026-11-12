package net.weaver.api.game.event.client;

import net.weaver.api.event.Event;

/**
 * A raw mouse button press or release on the client ({@code MouseHandler#onButton}),
 * before (PRE) and after (POST) vanilla handles it: screens, attacking, using,
 * picking. Canceling PRE swallows the click; POST is not cancelable.
 */
public final class ClientMouseButtonEvent extends Event {
    public enum Phase {
        PRE,
        POST
    }

    /** GLFW action values. */
    public static final int RELEASE = 0;
    public static final int PRESS = 1;

    private final Phase phase;
    private final int button;
    private final int action;
    private final int modifiers;

    public ClientMouseButtonEvent(Phase phase, int button, int action, int modifiers) {
        super(phase == Phase.PRE);
        this.phase = phase;
        this.button = button;
        this.action = action;
        this.modifiers = modifiers;
    }

    public Phase phase() {
        return phase;
    }

    /** The GLFW button: 0 left, 1 right, 2 middle, then side buttons. */
    public int button() {
        return button;
    }

    /** {@link #PRESS} or {@link #RELEASE}. */
    public int action() {
        return action;
    }

    /** GLFW modifier bits (shift 1, control 2, alt 4, super 8). */
    public int modifiers() {
        return modifiers;
    }
}
