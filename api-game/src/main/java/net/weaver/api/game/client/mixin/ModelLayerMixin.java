package net.weaver.api.game.client.mixin;

import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.weaver.api.game.client.model.ModelLayerRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Merges Weaver model layer definitions into the vanilla root layer map.
 *
 * <p>Target verified against Minecraft 26.3:
 * {@code LayerDefinitions#createRoots()Ljava/util/Map;} at {@code RETURN}. The
 * vanilla implementation builds an immutable map, so the injection copies it
 * into a new mutable map and replaces the return value. The vanilla map is
 * never mutated.
 *
 * <p>This method runs while the client builds its {@code EntityModelSet}; a
 * definition registered later is not baked until the next resource reload. See
 * {@link ModelLayerRegistry} for the bake-order rules.
 */
@Mixin(LayerDefinitions.class)
public abstract class ModelLayerMixin
{
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();

    @Inject(method = "createRoots", at = @At("RETURN"), cancellable = true, require = 1)
    private static void weaverCreateRoots(
            CallbackInfoReturnable<Map<ModelLayerLocation, LayerDefinition>> callback)
    {
        Map<ModelLayerLocation, LayerDefinition> vanilla = callback.getReturnValue();
        if (vanilla == null)
        {
            return;
        }
        try
        {
            Map<ModelLayerLocation, LayerDefinition> merged =
                    ModelLayerRegistry.merge(vanilla);
            if (merged != vanilla)
            {
                callback.setReturnValue(merged);
            }
        }
        catch (Throwable failure)
        {
            if (REPORTED_FAILURE.compareAndSet(false, true))
            {
                System.err.println("[weaver] MODEL LAYER HOOK FAILED reason=" + failure);
            }
        }
    }
}
