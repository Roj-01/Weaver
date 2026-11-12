package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.weaver.api.game.event.support.CrashShield;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Extracting the render state of a failing entity or block entity skips it for the frame (see {@link CrashShield}). */
@Mixin(LevelExtractor.class)
public abstract class CrashShieldExtractMixin {
    @WrapOperation(method = "extractVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"))
    private EntityRenderState weaver$guardEntity(LevelExtractor self, Entity entity, float partialTick,
            Operation<EntityRenderState> original) {
        if (!CrashShield.ENABLED) {
            return original.call(self, entity, partialTick);
        }
        try {
            return original.call(self, entity, partialTick);
        } catch (Throwable failure) {
            if (CrashShield.fatal(failure)) {
                throw CrashShield.rethrow(failure);
            }
            CrashShield.report("entity-extract", String.valueOf(entity.getType()), "skipped-frame", failure);
            return null;
        }
    }

    @WrapOperation(method = "extractVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean weaver$skipFailed(List<Object> states, Object state, Operation<Boolean> original) {
        return state != null && original.call(states, state);
    }

    @WrapOperation(method = "extractVisibleBlockEntities(Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/state/level/LevelRenderState;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Z)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;"))
    private BlockEntityRenderState weaver$guardBlockEntity(BlockEntityRenderDispatcher dispatcher, BlockEntity blockEntity,
            float partialTick, ModelFeatureRenderer.CrumblingOverlay crumbling, boolean outline,
            Operation<BlockEntityRenderState> original) {
        if (!CrashShield.ENABLED) {
            return original.call(dispatcher, blockEntity, partialTick, crumbling, outline);
        }
        try {
            return original.call(dispatcher, blockEntity, partialTick, crumbling, outline);
        } catch (Throwable failure) {
            if (CrashShield.fatal(failure)) {
                throw CrashShield.rethrow(failure);
            }
            CrashShield.report("block-entity-extract", String.valueOf(blockEntity.getType()), "skipped-frame", failure);
            return null;
        }
    }
}
