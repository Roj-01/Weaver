package net.weaver.clientproof.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.weaver.core.client.CoreClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin
{
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void weaverclientproof$renderHud(GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker, CallbackInfo callbackInfo)
    {
        CoreClientHooks.renderHud(graphics);
    }
}
