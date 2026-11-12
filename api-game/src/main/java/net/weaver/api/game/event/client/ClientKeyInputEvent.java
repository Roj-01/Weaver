package net.weaver.api.game.event.client;

import net.minecraft.client.input.KeyEvent;
import net.weaver.api.event.Event;

/**
 * Fired on the client after the game handled a keyboard key press, repeat, or
 * release ({@code KeyboardHandler#keyPress}), whether or not a screen is open.
 * Not cancelable; use key mappings to react to bound keys.
 */
public final class ClientKeyInputEvent extends Event {
    /** GLFW action codes. */
    public static final int RELEASE = 0;
    public static final int PRESS = 1;
    public static final int REPEAT = 2;

    private final KeyEvent key;
    private final int action;

    public ClientKeyInputEvent(KeyEvent key, int action) {
        this.key = key;
        this.action = action;
    }

    /** The key, scancode, and modifier flags. */
    public KeyEvent key() {
        return key;
    }

    /** {@link #RELEASE}, {@link #PRESS}, or {@link #REPEAT}. */
    public int action() {
        return action;
    }
}
