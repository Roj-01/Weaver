package net.weaver.api.game.event.mixin.client;

import java.util.HashMap;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.ModelBakingCompletedEvent;
import net.weaver.api.game.event.client.ModifyBakingResultEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Baked models switched in. {@code apply} takes a package-private record, so
 * the handler declares only the callback info. The baking result is changed in
 * the reload step that builds the reload state (a synthetic lambda in 26.3).
 */
@Mixin(ModelManager.class)
public abstract class ModelManagerEventsMixin {
    @Inject(method = "apply(Lnet/minecraft/client/resources/model/ModelManager$ReloadState;)V", at = @At("RETURN"))
    private void weaver$baked(CallbackInfo callback) {
        GameEvents.post(new ModelBakingCompletedEvent((ModelManager) (Object) this), LogicalSide.CLIENT);
    }

    @ModifyVariable(method = "lambda$loadModels$1(Lnet/minecraft/client/resources/model/sprite/MaterialBaker;Lit/unimi/dsi/fastutil/objects/Object2IntMap;Lnet/minecraft/client/model/geom/EntityModelSet;Lnet/minecraft/client/resources/model/ModelBakery$BakingResult;Ljava/util/Map;)Lnet/minecraft/client/resources/model/ModelManager$ReloadState;",
            at = @At("HEAD"), argsOnly = true)
    private static ModelBakery.BakingResult weaver$modifyBaking(ModelBakery.BakingResult result) {
        if (!GameEvents.hasListeners(ModifyBakingResultEvent.class)) {
            return result;
        }
        ModelBakery.BakingResult mutable = new ModelBakery.BakingResult(result.missingModels(),
                new HashMap<>(result.blockStateModels()), new HashMap<>(result.itemStackModels()),
                new HashMap<>(result.itemProperties()));
        GameEvents.post(new ModifyBakingResultEvent(mutable), LogicalSide.CLIENT);
        mutable.blockStateModels().values().removeIf(java.util.Objects::isNull);
        mutable.itemStackModels().values().removeIf(java.util.Objects::isNull);
        mutable.itemProperties().values().removeIf(java.util.Objects::isNull);
        return mutable;
    }
}
