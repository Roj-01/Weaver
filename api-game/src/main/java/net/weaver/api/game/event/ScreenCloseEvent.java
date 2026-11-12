package net.weaver.api.game.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Typed variant of {@link net.weaver.api.event.events.ScreenCloseEvent}.
 *
 * <p>The payloads are narrowed to the game types. The screen may be
 * {@code null} when the event only reports that the current screen is being
 * removed. The inherited accessors stay available for binary compatibility.
 */
public final class ScreenCloseEvent extends net.weaver.api.event.events.ScreenCloseEvent {

    /**
     * Creates a typed screen-close event.
     *
     * @param minecraft the client instance
     * @param screen    the screen being replaced or closed, possibly {@code null}
     */
    public ScreenCloseEvent(Object minecraft, Object screen) {
        super(minecraft, screen);
    }

    @Override
    public Minecraft minecraft() {
        return (Minecraft) super.minecraft();
    }

    @Override
    public Screen screen() {
        return (Screen) super.screen();
    }
}
