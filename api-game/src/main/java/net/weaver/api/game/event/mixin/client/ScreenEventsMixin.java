package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screens.Screen;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.ScreenInitEvent;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.support.ScreenWidgets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Screen widget setup. The event wraps the screen's own {@code init()} call,
 * both when the screen opens ({@code init(int, int)}) and when it rebuilds
 * its widgets ({@code rebuildWidgets()}).
 */
@Mixin(Screen.class)
public abstract class ScreenEventsMixin {
    @WrapOperation(method = "init(II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;init()V"))
    private void weaver$init(Screen screen, Operation<Void> original) {
        weaver$wrapInit(screen, original);
    }

    @WrapOperation(method = "rebuildWidgets()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;init()V"))
    private void weaver$rebuild(Screen screen, Operation<Void> original) {
        weaver$wrapInit(screen, original);
    }

    @org.spongepowered.asm.mixin.Unique
    private static void weaver$wrapInit(Screen screen, Operation<Void> original) {
        if (!GameEvents.hasListeners(ScreenInitEvent.class)) {
            original.call(screen);
            return;
        }
        ScreenInitEvent pre = GameEvents.post(new ScreenInitEvent(ScreenInitEvent.Phase.PRE, screen, null), LogicalSide.CLIENT);
        if (!GameEvents.canceled(pre)) {
            original.call(screen);
        }
        GameEvents.post(new ScreenInitEvent(ScreenInitEvent.Phase.POST, screen, ScreenWidgets.adder(screen)),
                LogicalSide.CLIENT);
    }
}
