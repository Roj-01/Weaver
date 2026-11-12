package net.weaver.api.game.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Typed variant of {@link net.weaver.api.event.events.ScreenOpenEvent}.
 *
 * <p>The payloads are narrowed to the game types. The inherited accessors stay
 * available with their {@link Object} descriptors for binary compatibility.
 */
public final class ScreenOpenEvent extends net.weaver.api.event.events.ScreenOpenEvent {

    /**
     * Creates a typed screen-open event.
     *
     * @param minecraft the client instance
     * @param screen    the screen being shown
     */
    public ScreenOpenEvent(Object minecraft, Object screen) {
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
