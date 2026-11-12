package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Invoker for the protected {@code Screen#addRenderableWidget}, used by {@code ScreenInitEvent}. */
@Mixin(Screen.class)
public interface ScreenWidgetInvoker {
    @Invoker("addRenderableWidget")
    GuiEventListener weaverAddRenderableWidget(GuiEventListener widget);
}
