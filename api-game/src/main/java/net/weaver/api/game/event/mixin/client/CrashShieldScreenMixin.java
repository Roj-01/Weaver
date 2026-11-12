package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.weaver.api.game.event.support.ClientCrashShield;
import net.weaver.api.game.event.support.CrashShield;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A screen that throws while ticking or drawing is closed instead of crashing (see {@link CrashShield}). */
@Mixin(Gui.class)
public abstract class CrashShieldScreenMixin {
    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;tick()V"))
    private void weaver$guardTick(Screen screen, Operation<Void> original) {
        if (!CrashShield.ENABLED) {
            original.call(screen);
            return;
        }
        try {
            original.call(screen);
        } catch (Throwable failure) {
            if (CrashShield.fatal(failure)) {
                throw CrashShield.rethrow(failure);
            }
            ClientCrashShield.screenFailed(screen, "tick", failure);
        }
    }

    @WrapOperation(method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void weaver$guardDraw(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
            Operation<Void> original) {
        if (!CrashShield.ENABLED) {
            original.call(screen, graphics, mouseX, mouseY, partialTick);
            return;
        }
        try {
            original.call(screen, graphics, mouseX, mouseY, partialTick);
        } catch (Throwable failure) {
            if (CrashShield.fatal(failure)) {
                throw CrashShield.rethrow(failure);
            }
            ClientCrashShield.screenFailed(screen, "draw", failure);
        }
    }
}
