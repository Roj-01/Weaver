package net.weaver.api.game.client.mixin;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.weaver.api.game.client.blockentity.BlockEntityRendererRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Merges Weaver block entity renderers into the map vanilla builds in
 * {@code BlockEntityRenderers#createEntityRenderers} (an immutable map in 26.3,
 * copied, never changed).
 */
@Mixin(BlockEntityRenderers.class)
public abstract class BlockEntityRendererMixin
{
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();

    @Inject(method = "createEntityRenderers(Lnet/minecraft/client/renderer/blockentity/BlockEntityRendererProvider$Context;)Ljava/util/Map;",
            at = @At("RETURN"), cancellable = true, require = 1)
    private static void weaverCreateBlockEntityRenderers(BlockEntityRendererProvider.Context context,
            CallbackInfoReturnable<Map<BlockEntityType<?>, BlockEntityRenderer<?, ?>>> callback)
    {
        try
        {
            Map<BlockEntityType<?>, BlockEntityRenderer<?, ?>> vanilla = callback.getReturnValue();
            Map<BlockEntityType<?>, BlockEntityRenderer<?, ?>> merged = BlockEntityRendererRegistry.merge(vanilla, context);
            if (merged != vanilla)
            {
                callback.setReturnValue(merged);
            }
        }
        catch (Throwable failure)
        {
            if (REPORTED_FAILURE.compareAndSet(false, true))
            {
                System.err.println("[weaver] BLOCK ENTITY RENDERER HOOK FAILED reason=" + failure);
            }
        }
    }
}
