package net.weaver.api.game.client.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invoker for the protected final
 * {@code LivingEntityRenderer#addLayer(RenderLayer)} method.
 *
 * <p>The method is final, so an {@link Invoker} is safer than a shadow
 * override. The invoker is only used by {@link EntityLayerMixin} after the
 * renderer's base constructor has assigned the model.
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererInvoker
{
    /**
     * Adds one layer to the renderer.
     *
     * @param layer the layer, never {@code null}
     * @return the target's own add result
     */
    @Invoker("addLayer")
    boolean weaverAddLayer(RenderLayer<?, ?> layer);
}
