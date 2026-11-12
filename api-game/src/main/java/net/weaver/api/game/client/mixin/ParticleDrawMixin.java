package net.weaver.api.game.client.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.List;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.QuadParticleFeatureRenderer;
import net.minecraft.client.renderer.oit.OitStage;
import net.weaver.api.game.client.level.LevelDrawRegistry;
import net.weaver.api.game.client.level.LevelDrawStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link LevelDrawStage#AFTER_PARTICLES}: right after the particle renderer draws
 * a group of translucent particles, inside the translucent features after terrain
 * (classic transparency) or a transparency layer (improved transparency), with the
 * pass the particles were drawn into.
 */
@Mixin(QuadParticleFeatureRenderer.class)
public abstract class ParticleDrawMixin {
    @Inject(method = "executeGroup", at = @At("RETURN"))
    private void weaver$afterParticles(FeatureFrameContext context, OitStage layer, RenderPass pass, int group,
            List<QuadParticleFeatureRenderer.Submit> submits, boolean strictlyOrdered, CallbackInfo callback) {
        if (!submits.isEmpty() && submits.get(0).translucent()) {
            LevelDrawRegistry.hook(LevelDrawStage.AFTER_PARTICLES, pass, layer == null ? null : String.valueOf(layer), null);
        }
    }
}
