package net.weaver.api.game.event.support;

import java.util.function.Function;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.weaver.api.game.event.mixin.client.ScreenWidgetInvoker;

/**
 * Builds the widget adder handed to {@code ScreenInitEvent}. Kept out of the
 * mixin because mixin code here does not use lambdas. Client only.
 */
public final class ScreenWidgets {
    private ScreenWidgets() {
    }

    public static Function<Object, Object> adder(Screen screen) {
        return widget -> ((ScreenWidgetInvoker) screen).weaverAddRenderableWidget((GuiEventListener) widget);
    }
}
