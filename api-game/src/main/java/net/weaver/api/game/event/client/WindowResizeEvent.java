package net.weaver.api.game.event.client;

import com.mojang.blaze3d.platform.Window;
import net.weaver.api.event.Event;

/**
 * Fired on the client after the window size or GUI scale changed and the GUI
 * scale was recomputed ({@code Minecraft#resizeGui}).
 */
public final class WindowResizeEvent extends Event {
    private final Window window;

    public WindowResizeEvent(Window window) {
        this.window = window;
    }

    /** The window; read its new size and GUI scale from it. */
    public Window window() {
        return window;
    }
}
