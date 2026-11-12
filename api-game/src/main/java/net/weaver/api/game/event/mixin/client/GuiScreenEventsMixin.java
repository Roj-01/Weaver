package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.ScreenOpeningEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code ScreenOpeningEvent} at the start of {@code Gui#setScreen}. A replaced
 * screen cancels the call and calls {@code setScreen} again with the new
 * screen; a per-thread marker keeps that call from posting the event again.
 */
@Mixin(Gui.class)
public abstract class GuiScreenEventsMixin {
    @Unique
    private static final ThreadLocal<Boolean> weaver$reinvoking = new ThreadLocal<>();

    @Shadow
    public abstract Screen screen();

    @Shadow
    public abstract void setScreen(Screen screen);

    @Inject(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"), cancellable = true)
    private void weaver$opening(Screen next, CallbackInfo callback) {
        if (weaver$reinvoking.get() != null) {
            weaver$reinvoking.remove(); // this is the call made below: only that one call skips the event
            return;
        }
        if (next == null || !GameEvents.hasListeners(ScreenOpeningEvent.class)) {
            return;
        }
        ScreenOpeningEvent event = GameEvents.post(new ScreenOpeningEvent(screen(), next), LogicalSide.CLIENT);
        if (GameEvents.canceled(event)) {
            callback.cancel();
            return;
        }
        Screen chosen = event.newScreen();
        if (chosen != next) {
            callback.cancel();
            weaver$reinvoking.set(Boolean.TRUE);
            try {
                setScreen(chosen);
            } finally {
                weaver$reinvoking.remove();
            }
        }
    }
}
