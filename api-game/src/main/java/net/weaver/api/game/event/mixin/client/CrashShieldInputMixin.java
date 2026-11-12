package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.weaver.api.game.event.support.ClientCrashShield;
import net.weaver.api.game.event.support.CrashShield;
import org.spongepowered.asm.mixin.Mixin;

/** A screen or key handler that throws on mouse input closes the screen instead of crashing (see {@link CrashShield}). */
@Mixin(MouseHandler.class)
public abstract class CrashShieldInputMixin {
    @WrapMethod(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V")
    private void weaver$guardButton(long window, MouseButtonInfo button, int action, Operation<Void> original) {
        try {
            original.call(window, button, action);
        } catch (Throwable failure) {
            if (!CrashShield.ENABLED || CrashShield.fatal(failure)) {
                throw CrashShield.rethrow(failure);
            }
            ClientCrashShield.inputFailed("mouse-button", failure);
        }
    }

    @WrapMethod(method = "onScroll(JDD)V")
    private void weaver$guardScroll(long window, double x, double y, Operation<Void> original) {
        try {
            original.call(window, x, y);
        } catch (Throwable failure) {
            if (!CrashShield.ENABLED || CrashShield.fatal(failure)) {
                throw CrashShield.rethrow(failure);
            }
            ClientCrashShield.inputFailed("mouse-scroll", failure);
        }
    }

    @WrapMethod(method = "onMove(JDDDD)V")
    private void weaver$guardMove(long window, double x, double y, double dx, double dy, Operation<Void> original) {
        try {
            original.call(window, x, y, dx, dy);
        } catch (Throwable failure) {
            if (!CrashShield.ENABLED || CrashShield.fatal(failure)) {
                throw CrashShield.rethrow(failure);
            }
            ClientCrashShield.inputFailed("mouse-move", failure);
        }
    }
}
