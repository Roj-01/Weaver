package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.RenderLivingEvent;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.support.PoseStacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Living entities (players included) submitted for drawing. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingRendererEventsMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void weaver$renderPre(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                  CameraRenderState camera, CallbackInfo callback,
                                  @Share("depth") LocalIntRef depth) {
        depth.set(-1);
        if (!GameEvents.hasListeners(RenderLivingEvent.class)) {
            return;
        }
        int before = PoseStacks.depth(poseStack);
        depth.set(before);
        if (GameEvents.canceled(GameEvents.post(new RenderLivingEvent(RenderLivingEvent.Phase.PRE,
                (LivingEntityRenderer<?, ?, ?>) (Object) this, state, poseStack, collector, camera), LogicalSide.CLIENT))) {
            // POST will not run: undo whatever a PRE listener pushed.
            PoseStacks.restore(poseStack, before, "RenderLivingEvent");
            callback.cancel();
        }
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("RETURN"))
    private void weaver$renderPost(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                   CameraRenderState camera, CallbackInfo callback,
                                   @Share("depth") LocalIntRef depth) {
        if (GameEvents.hasListeners(RenderLivingEvent.class)) {
            GameEvents.post(new RenderLivingEvent(RenderLivingEvent.Phase.POST,
                    (LivingEntityRenderer<?, ?, ?>) (Object) this, state, poseStack, collector, camera), LogicalSide.CLIENT);
        }
        if (depth.get() >= 0) {
            PoseStacks.restore(poseStack, depth.get(), "RenderLivingEvent");
        }
    }
}
