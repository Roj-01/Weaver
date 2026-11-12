package net.weaver.api.game.client.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.entity.EntityType;
import net.weaver.api.game.client.entity.EntityRendererRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Merges Weaver entity renderer providers into the vanilla renderer map.
 *
 * <p>Target verified against Minecraft 26.3:
 * {@code EntityRenderers#createEntityRenderers(EntityRendererProvider$Context)}
 * returns a {@code Map} that vanilla builds through a Guava
 * {@code ImmutableMap.Builder}. The injection at {@code RETURN} copies the
 * vanilla map into a new mutable map, creates the registered renderers through
 * their providers, and replaces the return value. The vanilla map instance is
 * never mutated.
 */
@Mixin(EntityRenderers.class)
public abstract class EntityRendererMixin
{
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();

    @Inject(method = "createEntityRenderers", at = @At("RETURN"), cancellable = true, require = 1)
    private static void weaverCreateEntityRenderers(EntityRendererProvider.Context context,
            CallbackInfoReturnable<Map<EntityType<?>, EntityRenderer<?, ?>>> callback)
    {
        Map<EntityType<?>, EntityRenderer<?, ?>> vanilla = callback.getReturnValue();
        if (vanilla == null)
        {
            return;
        }
        try
        {
            Map<EntityType<?>, EntityRenderer<?, ?>> merged =
                    EntityRendererRegistry.merge(vanilla, context);
            if (merged != vanilla)
            {
                callback.setReturnValue(merged);
            }
        }
        catch (Throwable failure)
        {
            if (REPORTED_FAILURE.compareAndSet(false, true))
            {
                System.err.println("[weaver] ENTITY RENDERER HOOK FAILED reason=" + failure);
            }
        }
    }
}
