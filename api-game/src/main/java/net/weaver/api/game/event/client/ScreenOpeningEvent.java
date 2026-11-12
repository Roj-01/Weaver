package net.weaver.api.game.event.client;

import net.minecraft.client.gui.screens.Screen;
import net.weaver.api.event.Event;

/**
 * Fired on the client when a screen is asked to open ({@code Gui#setScreen}
 * with a screen), before anything changes: the current screen is still open.
 * Cancelable: a canceled event leaves the current screen (or the world) as it
 * is. {@link #setNewScreen(Screen)} opens another screen instead; setting
 * {@code null} closes the current screen.
 *
 * <p>Not fired for {@code setScreen(null)}, which closes the screen (the game
 * may then open the title or death screen itself). {@code ScreenOpenEvent} and
 * {@code ScreenCloseEvent} still report what was finally shown.</p>
 */
public final class ScreenOpeningEvent extends Event {
    private final Screen currentScreen;
    private final Screen screen;
    private Screen newScreen;

    public ScreenOpeningEvent(Screen currentScreen, Screen screen) {
        super(true);
        this.currentScreen = currentScreen;
        this.screen = screen;
        this.newScreen = screen;
    }

    /** The screen open now, or {@code null} in the world. */
    public Screen currentScreen() {
        return currentScreen;
    }

    /** The screen that was asked for. */
    public Screen screen() {
        return screen;
    }

    /** The screen that will open; {@link #screen()} unless a listener changed it. */
    public Screen newScreen() {
        return newScreen;
    }

    /** Opens {@code screen} instead ({@code null} closes the current screen). */
    public void setNewScreen(Screen screen) {
        this.newScreen = screen;
    }
}
