package net.weaver.api.game.client.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * Creates one render layer for a registered living entity renderer.
 *
 * <p>Factories are called from the constructor return of
 * {@code LivingEntityRenderer}, after the model has been assigned but before a
 * concrete subclass constructor body has run. A factory must therefore rely on
 * the renderer's model, the provider context, and the renderer object itself;
 * it must not read primitive or otherwise uninitialized fields of a concrete
 * renderer subclass.
 *
 * <p>The wildcard return type matches the erased
 * {@code LivingEntityRenderer#addLayer(RenderLayer)} signature. Implementations
 * commonly cast the supplied renderer to their concrete type. The returned
 * layer's state and model type arguments must match the renderer; erasure means
 * the registry cannot verify that at registration time, and an incompatible
 * layer fails when vanilla invokes it.
 */
@FunctionalInterface
public interface EntityLayerFactory
{
    /**
     * Creates a layer.
     *
     * @param renderer the renderer being constructed, never {@code null}
     * @param context  the renderer provider context, normally non-null
     * @return the layer to add, or {@code null} to skip this renderer
     */
    RenderLayer<?, ?> createLayer(LivingEntityRenderer<?, ?, ?> renderer,
                                  EntityRendererProvider.Context context);
}
