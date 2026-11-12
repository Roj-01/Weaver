package net.weaver.api.game.client.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderPipelines;
import net.weaver.api.game.client.render.CustomRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mod pipelines are compiled with the vanilla ones at each resource reload. */
@Mixin(RenderPipelines.class)
public abstract class CustomRenderingMixin
{
    @Inject(method = "requiredPipelines()Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private static void weaver$modPipelines(CallbackInfoReturnable<List<RenderPipeline>> callback)
    {
        List<RenderPipeline> mods = CustomRendering.precompiled();
        if (!mods.isEmpty())
        {
            List<RenderPipeline> all = new ArrayList<>(callback.getReturnValue());
            for (RenderPipeline pipeline : mods)
            {
                if (!all.contains(pipeline))
                {
                    all.add(pipeline);
                }
            }
            callback.setReturnValue(all);
        }
    }
}
