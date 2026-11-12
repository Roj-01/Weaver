package net.weaver.api.game.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.weaver.api.game.client.render.PipelineModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Hands every pipeline the game asks to draw with to the pushed
 * {@link PipelineModifiers}. {@code RenderSystem#getCompiledPipeline} goes
 * through {@code getCompiledPipelineNullable}, so that one method is enough.
 */
@Mixin(RenderSystem.class)
public abstract class PipelineModifierMixin {
    @ModifyVariable(method = "getCompiledPipelineNullable(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;",
            at = @At("HEAD"), argsOnly = true, require = 1)
    private static RenderPipeline weaver$modify(RenderPipeline pipeline) {
        return PipelineModifiers.apply(pipeline);
    }
}
