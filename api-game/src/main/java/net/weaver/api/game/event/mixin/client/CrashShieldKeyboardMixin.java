package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.weaver.api.game.event.support.ClientCrashShield;
import net.weaver.api.game.event.support.CrashShield;
import org.spongepowered.asm.mixin.Mixin;

/** A screen or key handler that throws on keyboard input closes the screen instead of crashing (see {@link CrashShield}). */
@Mixin(KeyboardHandler.class)
public abstract class CrashShieldKeyboardMixin {
    @WrapMethod(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V")
    private void weaver$guardKey(long window, int action, KeyEvent event, Operation<Void> original) {
        try {
            original.call(window, action, event);
        } catch (Throwable failure) {
            if (!CrashShield.ENABLED || CrashShield.fatal(failure)) {
                throw CrashShield.rethrow(failure);
            }
            ClientCrashShield.inputFailed("key", failure);
        }
    }

    @WrapMethod(method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V")
    private void weaver$guardChar(long window, CharacterEvent event, Operation<Void> original) {
        try {
            original.call(window, event);
        } catch (Throwable failure) {
            if (!CrashShield.ENABLED || CrashShield.fatal(failure)) {
                throw CrashShield.rethrow(failure);
            }
            ClientCrashShield.inputFailed("char", failure);
        }
    }
}
