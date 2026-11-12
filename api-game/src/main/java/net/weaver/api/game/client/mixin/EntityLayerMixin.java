package net.weaver.api.game.client.mixin;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.weaver.api.game.client.entity.EntityLayerRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adds registered Weaver layers to living entity renderers.
 *
 * <p>Target verified against Minecraft 26.3:
 * {@code LivingEntityRenderer#<init>(EntityRendererProvider$Context, M,
 * float)V} at {@code RETURN} with the erased descriptor
 * {@code (EntityRendererProvider$Context, EntityModel, float)V}. This is the
 * safest verified path:
 * <ul>
 *     <li>every concrete living renderer must call the base constructor;</li>
 *     <li>the {@code model} field is already assigned when the method
 *     returns;</li>
 *     <li>the provider context is still available.</li>
 * </ul>
 *
 * <p>An injection on {@code addLayer} would miss renderers that never add a
 * vanilla layer. The cost of the constructor path is ordering: Weaver layers
 * are added before the concrete subclass's own layers, so they render below
 * them. Layer factories must not depend on subclass constructor state.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class EntityLayerMixin
{
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void weaverAttachLayers(EntityRendererProvider.Context context,
                                    EntityModel<?> model, float shadowRadius,
                                    CallbackInfo callback)
    {
        try
        {
            LivingEntityRenderer<?, ?, ?> renderer =
                    (LivingEntityRenderer<?, ?, ?>) (Object) this;
            LivingEntityRendererInvoker invoker =
                    (LivingEntityRendererInvoker) (Object) this;
            EntityLayerRegistry.attach(renderer, context, invoker::weaverAddLayer);
        }
        catch (Throwable failure)
        {
            if (REPORTED_FAILURE.compareAndSet(false, true))
            {
                System.err.println("[weaver] ENTITY LAYER HOOK FAILED reason=" + failure);
            }
        }
    }
}
