package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.weaver.api.game.event.support.CrashShield;
import net.weaver.api.game.event.support.PoseStacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** An entity or block entity that fails to draw is skipped for the frame (see {@link CrashShield}). */
@Mixin(LevelRenderer.class)
public abstract class CrashShieldRenderMixin {
    @WrapOperation(method = "submitEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    private void weaver$guardEntity(EntityRenderDispatcher dispatcher, EntityRenderState state, CameraRenderState camera,
            double x, double y, double z, PoseStack poses, SubmitNodeCollector collector, Operation<Void> original) {
        if (!CrashShield.ENABLED) {
            original.call(dispatcher, state, camera, x, y, z, poses, collector);
            return;
        }
        int depth = PoseStacks.depth(poses);
        try {
            original.call(dispatcher, state, camera, x, y, z, poses, collector);
        } catch (Throwable failure) {
            if (CrashShield.fatal(failure)) {
                throw CrashShield.rethrow(failure);
            }
            PoseStacks.restore(poses, depth, "entity-render");
            CrashShield.report("entity-render", String.valueOf(state.entityType), "skipped-frame", failure);
        }
    }

    @WrapOperation(method = "submitBlockEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private void weaver$guardBlockEntity(BlockEntityRenderDispatcher dispatcher, BlockEntityRenderState state,
            PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera, Operation<Void> original) {
        if (!CrashShield.ENABLED) {
            original.call(dispatcher, state, poses, collector, camera);
            return;
        }
        int depth = PoseStacks.depth(poses);
        try {
            original.call(dispatcher, state, poses, collector, camera);
        } catch (Throwable failure) {
            if (CrashShield.fatal(failure)) {
                throw CrashShield.rethrow(failure);
            }
            PoseStacks.restore(poses, depth, "block-entity-render");
            CrashShield.report("block-entity-render", String.valueOf(state.blockEntityType), "skipped-frame", failure);
        }
    }
}
