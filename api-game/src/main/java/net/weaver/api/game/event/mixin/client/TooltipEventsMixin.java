package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.RenderTooltipEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tooltip drawing: the start (position, font) and the background sprite. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class TooltipEventsMixin {
    @Unique
    private static final ThreadLocal<Object> weaver$reinvoking = new ThreadLocal<>();

    @Unique
    private List<ClientTooltipComponent> weaver$lines;

    @Unique
    private Font weaver$font;

    @Inject(method = "tooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void weaver$tooltipPre(Font font, List<ClientTooltipComponent> lines, int x, int y,
                                   ClientTooltipPositioner positioner, Identifier style, boolean extraSpace, CallbackInfo callback) {
        weaver$lines = lines;
        weaver$font = font;
        if (weaver$reinvoking.get() == this || !GameEvents.hasListeners(RenderTooltipEvent.class)) {
            return;
        }
        GuiGraphicsExtractor self = (GuiGraphicsExtractor) (Object) this;
        RenderTooltipEvent event = GameEvents.post(new RenderTooltipEvent(RenderTooltipEvent.Phase.PRE, self, lines, font,
                x, y, style), LogicalSide.CLIENT);
        if (GameEvents.canceled(event)) {
            callback.cancel();
        } else if (event.x() != x || event.y() != y || event.font() != font) {
            callback.cancel();
            Object previous = weaver$reinvoking.get();
            weaver$reinvoking.set(this);
            try {
                self.tooltip(event.font(), lines, event.x(), event.y(), positioner, style, extraSpace);
            } finally {
                weaver$reinvoking.set(previous);
            }
        }
    }

    @WrapOperation(method = "tooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/TooltipRenderUtil;extractTooltipBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIILnet/minecraft/resources/Identifier;)V"))
    private void weaver$tooltipBackground(GuiGraphicsExtractor graphics, int x, int y, int width, int height, Identifier style,
                                          Operation<Void> original) {
        Identifier sprite = style;
        if (GameEvents.hasListeners(RenderTooltipEvent.class)) {
            sprite = GameEvents.post(new RenderTooltipEvent(RenderTooltipEvent.Phase.BACKGROUND, graphics,
                    weaver$lines == null ? List.of() : weaver$lines, weaver$font, x, y, style), LogicalSide.CLIENT).background();
        }
        original.call(graphics, x, y, width, height, sprite);
    }
}
