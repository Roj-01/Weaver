package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRendererMap;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.RegisterFeatureRenderersEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mod feature renderers, added once the vanilla ones are in place. */
@Mixin(FeatureRenderDispatcher.class)
public abstract class FeatureRenderersEventsMixin {
    @Shadow
    @Final
    private FeatureRendererMap featureRenderers;

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/RenderBuffers;Lnet/minecraft/client/resources/model/ModelManager;Lnet/minecraft/client/resources/model/sprite/AtlasManager;Lnet/minecraft/client/gui/Font;Lnet/minecraft/client/renderer/state/GameRenderState;)V",
            at = @At("RETURN"))
    private void weaver$renderers(RenderBuffers buffers, ModelManager models, AtlasManager atlases, Font font,
            GameRenderState state, CallbackInfo callback) {
        if (GameEvents.hasListeners(RegisterFeatureRenderersEvent.class)) {
            GameEvents.post(new RegisterFeatureRenderersEvent(featureRenderers, models, atlases, font), LogicalSide.CLIENT);
        }
    }
}
