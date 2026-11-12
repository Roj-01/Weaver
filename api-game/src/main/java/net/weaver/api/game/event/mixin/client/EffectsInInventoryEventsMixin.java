package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.GatherEffectScreenTooltipsEvent;
import net.weaver.api.game.event.client.InventoryEffectsRenderEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Active effects drawn beside an inventory screen, and their hover tooltips. */
@Mixin(EffectsInInventory.class)
public abstract class EffectsInInventoryEventsMixin {
    @Shadow
    @Final
    private AbstractContainerScreen<?> screen;

    @Unique
    private int weaver$mouseX;

    @Unique
    private int weaver$mouseY;

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V", at = @At("HEAD"), cancellable = true)
    private void weaver$effects(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo callback) {
        weaver$mouseX = mouseX;
        weaver$mouseY = mouseY;
        if (GameEvents.hasListeners(InventoryEffectsRenderEvent.class)
                && GameEvents.canceled(GameEvents.post(new InventoryEffectsRenderEvent(screen), LogicalSide.CLIENT))) {
            callback.cancel();
        }
    }

    @WrapOperation(method = "extractEffects(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/util/Collection;IIIII)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/EffectsInInventory;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;IIZI)I"))
    private int weaver$tooltip(EffectsInInventory self, GuiGraphicsExtractor graphics, Font font, Component name,
            Component duration, int x, int y, boolean ambient, int maxWidth, Operation<Integer> original,
            @Local MobEffectInstance effect, @Local(argsOnly = true, ordinal = 1) int spacing) {
        int width = original.call(self, graphics, font, name, duration, x, y, ambient, maxWidth);
        if (weaver$mouseX >= x && weaver$mouseX < x + width && weaver$mouseY >= y && weaver$mouseY < y + spacing
                && GameEvents.hasListeners(GatherEffectScreenTooltipsEvent.class)) {
            List<Component> lines = new ArrayList<>(List.of(name, duration));
            GameEvents.post(new GatherEffectScreenTooltipsEvent(screen, effect, lines), LogicalSide.CLIENT);
            if (!lines.isEmpty()) {
                graphics.setComponentTooltipForNextFrame(font, lines, weaver$mouseX, weaver$mouseY);
            }
        }
        return width;
    }
}
