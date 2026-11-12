package net.weaver.api.game.client.gui;

import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** The "Mods" button that Weaver adds to the title screen and the pause menu. Internal. */
public final class MenuButtons {
    private MenuButtons() {
    }

    public static Button mods(int x, int y, int width) {
        return Button.builder(Component.translatableWithFallback("weaver.mods.button", "Mods"), MenuButtons::open)
                .bounds(x, y, width, 20).build();
    }

    private static void open(Button button) {
        ModListScreen.open();
    }

    public static Button find(List<? extends GuiEventListener> children, String key) {
        for (GuiEventListener child : children) {
            if (child instanceof Button button && button.getMessage().getContents() instanceof TranslatableContents contents
                    && key.equals(contents.getKey())) {
                return button;
            }
        }
        return null;
    }
}
