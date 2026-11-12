package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.BossBarRenderEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Boss bars on the HUD. For each bar, {@code extractRenderState} draws the bar
 * and then its name; the event is posted at the bar and its answer is reused
 * for the name.
 */
@Mixin(BossHealthOverlay.class)
public abstract class BossOverlayEventsMixin {
    @Unique
    private boolean weaver$skipName;

    @WrapOperation(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/BossHealthOverlay;extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;)V"))
    private void weaver$bar(BossHealthOverlay overlay, GuiGraphicsExtractor graphics, int x, int y, BossEvent event,
                            Operation<Void> original) {
        weaver$skipName = false;
        if (event instanceof LerpingBossEvent lerping && GameEvents.hasListeners(BossBarRenderEvent.class)
                && GameEvents.canceled(GameEvents.post(new BossBarRenderEvent(graphics, lerping, x, y), LogicalSide.CLIENT))) {
            weaver$skipName = true;
            return;
        }
        original.call(overlay, graphics, x, y, event);
    }

    @WrapOperation(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private void weaver$name(GuiGraphicsExtractor graphics, Font font, Component name, int x, int y, int color,
                             Operation<Void> original) {
        if (weaver$skipName) {
            weaver$skipName = false;
            return;
        }
        original.call(graphics, font, name, x, y, color);
    }
}
